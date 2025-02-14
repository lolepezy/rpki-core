package net.ripe.rpki.domain.signing;

import com.google.common.collect.Lists;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.ta.domain.request.ResourceCertificateRequestData;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.ResourceClassListQuery;
import net.ripe.rpki.domain.ResourceClassListResponse;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.domain.TestObjects.createTestKeyPair;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CertificateRequestCreationServiceBeanTest {

    private CertificateRequestCreationServiceBean subject;

    private KeyPairService keyPairService;

    private RepositoryConfiguration repositoryConfiguration;

    private AllResourcesCertificateAuthority allResourcesCa;
    private ManagedCertificateAuthority productionCa;

    private URI publicRepositoryUri = URI.create("rsync://localhost/foo/ca-repository-uri/");
    private UUID caId = UUID.randomUUID();

    @Before
    public void setUp() {
        allResourcesCa = mock(AllResourcesCertificateAuthority.class);
        when(allResourcesCa.processResourceClassListQuery(any())).thenAnswer((invocation) ->
            new ResourceClassListResponse(invocation.getArgument(0, ResourceClassListQuery.class).getResources())
        );

        productionCa = mock(ManagedCertificateAuthority.class);
        keyPairService = mock(KeyPairService.class);
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(publicRepositoryUri);
        subject = new CertificateRequestCreationServiceBean(repositoryConfiguration, keyPairService);

        when(productionCa.getParent()).thenReturn(allResourcesCa);
        when(productionCa.isProductionCa()).thenReturn(true);
        when(productionCa.getUuid()).thenReturn(caId);
    }

    @Test
    public void shouldNotInitiateRollIfKeyIsRecent() {
        when(productionCa.currentKeyPairIsOlder(1)).thenReturn(false);

        Optional<CertificateIssuanceRequest> req = subject.initiateKeyRoll(productionCa, 1);
        assertThat(req).isEmpty();

        verifyNoInteractions(keyPairService);
    }

    @Test
    public void shouldInitiateRoll() {
        // Expect that a new key is created and certificate is requested for it with the same resources
        // as we have on the current certificate
        when(productionCa.currentKeyPairIsOlder(anyInt())).thenReturn(true);
        KeyPairEntity currentKp = createTestKeyPair("CURRENT");
        IncomingResourceCertificate currentResourceCertificate = TestObjects.createResourceCertificate(1L, currentKp);
        when(productionCa.findCurrentIncomingResourceCertificate()).thenReturn(Optional.of(currentResourceCertificate));

        KeyPairEntity newKp = createTestKeyPair("NEW");
        when(productionCa.createNewKeyPair(any(KeyPairService.class))).thenReturn(newKp);

        Optional<CertificateIssuanceRequest> maybeRequest = subject.initiateKeyRoll(productionCa, 0);

        assertThat(maybeRequest).hasValueSatisfying(request -> {
            assertThat(request.getResources()).isEqualTo(currentResourceCertificate.getResources());
        });
    }

    @Test
    public void shouldNotInitiateRollWhenRollInProgress() {
        when(productionCa.hasRollInProgress()).thenReturn(true);

        Optional<CertificateIssuanceRequest> req = subject.initiateKeyRoll(productionCa, 0);

        assertThat(req).isEmpty();
    }

    @Test
    public void should_throw_exception_when_requesting_revocation_without_an_existing_old_key_pair() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> subject.createCertificateRevocationRequestForOldKey(productionCa));
        assertEquals("Cannot find an OLD key pair", illegalArgumentException.getMessage());
    }

    @Test
    public void should_create_revocation_request_when_there_is_an_old_key_pair() {
        KeyPairEntity keyPair = mock(KeyPairEntity.class);
        PublicKey publicKey = mock(PublicKey.class);
        when(keyPair.getStatus()).thenReturn(KeyPairStatus.OLD);
        when(keyPair.getPublicKey()).thenReturn(publicKey);

        when(productionCa.findOldKeyPair()).thenReturn(Optional.of(keyPair));

        CertificateRevocationRequest request = subject.createCertificateRevocationRequestForOldKey(productionCa);
        assertSame(publicKey, request.getSubjectPublicKey());
    }

    @Test
    public void should_not_request_member_certificates_if_key_pair_does_not_need_certificate() {
        List<CertificateIssuanceRequest> requests = subject.createCertificateIssuanceRequestForAllKeys(
            productionCa, IpResourceSet.parse("10/8"));

        assertTrue(requests.isEmpty());
    }

    @Test
    public void should_request_member_certificates_if_key_pair_requires_a_certificate() {
        givenKeyPairWithoutCurrentCertificate(productionCa);
        List<CertificateIssuanceRequest> requests = subject.createCertificateIssuanceRequestForAllKeys(
            productionCa, IpResourceSet.parse("10/8"));

        assertThat(requests).hasSize(1).allMatch(CertificateIssuanceRequest.class::isInstance);
    }

    @Test
    public void shouldCreateTrustAnchorRequestWithNotificationUriAsSiaDescriptor() throws Exception {
        TaRequest siginingRequest = new SigningRequest(mock(ResourceCertificateRequestData.class));
        List<TaRequest> siginingRequests = Lists.newArrayList(siginingRequest);
        URI repositoryUri = new URI("http://bla.com/notification.xml");
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(repositoryUri);

        TrustAnchorRequest trustAnchorRequest = subject.createTrustAnchorRequest(siginingRequests);

        assertEquals(1, trustAnchorRequest.getSiaDescriptors().length);
        assertEquals(repositoryUri, trustAnchorRequest.getSiaDescriptors()[0].getLocation());
    }

    @Test
    public void shouldNotInitiateMemberKeyRollWithoutCurrentCertificate() {
        when(productionCa.findCurrentIncomingResourceCertificate()).thenReturn(Optional.empty());

        assertThat(subject.initiateKeyRoll(productionCa, 1)).isEmpty();
    }

    private void givenKeyPairWithoutCurrentCertificate(ManagedCertificateAuthority ca) {
        KeyPairEntity keyPair = createTestKeyPair();
        ca.addKeyPair(keyPair);
        when(ca.getKeyPairs()).thenReturn(Collections.singletonList(keyPair));
        KeyPairEntity spy = spy(keyPair);
        given(spy.findCurrentIncomingCertificate()).willReturn(null);
    }

}

package net.ripe.rpki.server.api.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class NonHostedCertificateAuthorityData extends CertificateAuthorityData {

    private final ProvisioningIdentityCertificate provisioningIdentityCertificate;
    private final Set<NonHostedPublicKeyData> publicKeys;

    public NonHostedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                             ProvisioningIdentityCertificate provisioningIdentityCertificate,
                                             IpResourceSet ipResourceSet,
                                             Set<NonHostedPublicKeyData> publicKeys) {
        super(versionedId, name, uuid, parentId, CertificateAuthorityType.NONHOSTED, ipResourceSet, null);

        this.provisioningIdentityCertificate = provisioningIdentityCertificate;
        this.publicKeys= publicKeys;
    }

}

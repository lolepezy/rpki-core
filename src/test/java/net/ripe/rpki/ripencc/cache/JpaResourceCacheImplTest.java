package net.ripe.rpki.ripencc.cache;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JpaResourceCacheImplTest extends CertificationDomainTestCase {

    @Autowired
    private JpaResourceCacheImpl resourceCache;

    @Autowired
    private EntityManager entityManager;

    @Before
    public void setUp() {
        resourceCache.setEntityManager(entityManager);
        inTx(() -> resourceCache.clearCache());
    }

    @Test
    public void testEmptyIsEmpty() {
        assertTrue(resourceCache.hasNoMemberResources());
    }

    @Test
    public void testEmptyAfterCleaning() {
        Map<CaName, IpResourceSet> m = new HashMap<>();
        m.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8"));
        m.put(CaName.fromMembershipId(2), IpResourceSet.parse("11.0.0.0/8"));
        inTx(() -> resourceCache.populateCache(m));
        assertFalse(resourceCache.hasNoMemberResources());
        inTx(() -> resourceCache.clearCache());
        assertTrue(resourceCache.hasNoMemberResources());
    }

    @Test
    public void testLookupAfterPopulate() {
        Map<CaName, IpResourceSet> m = new HashMap<>();
        m.put(CaName.fromMembershipId(1), IpResourceSet.parse("10.0.0.0/8"));
        m.put(CaName.fromMembershipId(2), IpResourceSet.parse("11.0.0.0/8"));
        inTx(() -> resourceCache.populateCache(m));

        assertEquals(Optional.of(IpResourceSet.parse("10.0.0.0/8")), resourceCache.lookupResources(CaName.fromMembershipId(1)));
        assertEquals(Optional.of(IpResourceSet.parse("11.0.0.0/8")), resourceCache.lookupResources(CaName.fromMembershipId(2)));
        assertEquals(Optional.empty(), resourceCache.lookupResources(CaName.fromMembershipId(3)));
    }

}

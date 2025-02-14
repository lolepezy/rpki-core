package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.CaStatCaEvent;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CaStatRoaEvent;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.util.JdbcDBComponent;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository(value = "jpaCertificateAuthorityRepository")
@SuppressWarnings("java:S1192")
public class JpaCertificateAuthorityRepository extends JpaRepository<CertificateAuthority> implements CertificateAuthorityRepository {

    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");

    @Override
    protected Class<CertificateAuthority> getEntityClass() {
        return CertificateAuthority.class;
    }

    @Override
    public CertificateAuthority get(Object id) throws EntityNotFoundException {
        CertificateAuthority result = super.get(id);
        if (!result.getId().equals(id)) {
            // Check for presence of Hibernate 4.1.9 bug. Sometimes the id of the proxy doesn't seem
            // to be initialized correctly, especially on faster machines. Hibernate 4.1.8 doesn't
            // seem to have this problem, but who knows... - Erik 2013-2-4.
            throw new AssertionError("returned object's id does not match. Got <" + result.getId() + "> expected <" + id + ">");
        }
        return result;
    }

    @Override
    public void add(CertificateAuthority entity) {
        super.add(entity);

        try {
            // Flush session to see if the new CA violates the unique name constraint
            manager.flush();
        } catch (PersistenceException e) {
            if (JdbcDBComponent.isUniqueConstraintViolation(e, "certificateauthority_name_key")) {
                throw new NameNotUniqueException(entity.getName());
            }
        }
    }

    @Override
    public CertificateAuthority findByName(X500Principal name) {
        return findByTypeAndName(CertificateAuthority.class, name);
    }

    @Override
    public ProductionCertificateAuthority findRootCAByName(X500Principal name) {
        return findByTypeAndName(ProductionCertificateAuthority.class, name);
    }

    @Override
    public AllResourcesCertificateAuthority findAllresourcesCAByName(X500Principal name) {
        return findByTypeAndName(AllResourcesCertificateAuthority.class, name);
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndName(Class<T> type, X500Principal name) {
        Validate.notNull(type, "name is null");

        try {
            Query query = createQuery("from " + type.getSimpleName() + " ca where upper(:name) = upper(ca.name)");
            return type.cast(query.setParameter("name", name.getName()).getSingleResult());
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndUuid(Class<T> type, UUID memberUuid, LockModeType lockModeType) {
        Validate.notNull(memberUuid, "memberUuid is null");
        try {
            Query query = createQuery("from " + type.getSimpleName() + " ca where uuid = :uuid")
                .setParameter("uuid", memberUuid)
                .setLockMode(lockModeType);
            return type.cast(query.getSingleResult());
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<CertificateAuthority> findAllByParent(ParentCertificateAuthority parent) {
        Validate.notNull(parent, "parent is null");
        Query query = createQuery("from CertificateAuthority ca where parent = :parent");
        return query.setParameter("parent", parent).getResultList();
    }

    @Override
    public ManagedCertificateAuthority findManagedCa(Long id) {
        try {
            Query query = createQuery("from ManagedCertificateAuthority ca where id = :id");
            return (ManagedCertificateAuthority) query.setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public NonHostedCertificateAuthority findNonHostedCa(Long id) {
        try {
            Query query = createQuery("from NonHostedCertificateAuthority ca where id = :id");
            return (NonHostedCertificateAuthority) query.setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<CaStat> getCAStats() {
        final Stream<Object[]> rowStream = manager.createNativeQuery("SELECT " +
                "ca.name, " +
                "count(rp.*), " +
                "ca.created_at " +
                "FROM certificateauthority ca " +
                "LEFT JOIN roaconfiguration r ON r.certificateauthority_id = ca.id " +
                "LEFT JOIN roaconfiguration_prefixes rp ON rp.roaconfiguration_id = r.id " +
                "GROUP BY ca.name, ca.created_at"
        ).getResultStream();

        return rowStream.map(row -> {
            String caName = toStr(row[0]);
            int roaCount = toInt(row[1]);
            Date createdAt = (Date) row[2];
            return new CaStat(caName, roaCount, ISO_DATE_FORMAT.print(new DateTime(createdAt)));
        }).collect(Collectors.toList());
    }

    @Override
    public Collection<CaStatEvent> getCAStatEvents() {
        final String updateRoaConf = "UpdateRoaConfigurationCommand";
        final String createRoaSpec = "CreateRoaSpecificationCommand";
        final String deleteRoaSpec = "DeleteRoaSpecificationCommand";
        final String activateCaSpec = "ActivateHostedCertificateAuthorityCommand";
        final String activateNonHostedCaSpec = "ActivateNonHostedCertificateAuthorityCommand";
        final String deleteCaSpec = "DeleteCertificateAuthorityCommand";
        final String deleteNonHostedCaSpec = "DeleteNonHostedCertificateAuthorityCommand";

        final List<String> commandTypes = Arrays.asList(updateRoaConf, createRoaSpec, deleteRoaSpec,
                activateCaSpec, activateNonHostedCaSpec, deleteCaSpec, deleteNonHostedCaSpec);

        final Pattern updateConfPattern = Pattern.compile("Updated ROA configuration. Additions: (.+). Deletions: (.+)\\.");
        final Pattern createSpecPattern = Pattern.compile("Created ROA specification '.+' (.+).");
        final Pattern deleteSpecPattern = Pattern.compile("Deleted ROA specification '.+' (.+).");

        final Query q = manager.createNativeQuery("SELECT " +
                "ca.name, " +
                "au.commandtype, " +
                "au.executiontime, " +
                "au.commandsummary " +
                "FROM commandAudit au " +
                "LEFT JOIN certificateAuthority ca ON ca.id = au.ca_id " +
                "WHERE commandtype IN (" + inClause(commandTypes) + ")" +
                "ORDER BY au.executiontime ASC, ca.name");

        final List<?> resultList = q.getResultList();
        final List<CaStatEvent> result = new ArrayList<>();
        for (final Object r : resultList) {
            final Object[] columns = (Object[]) r;
            final String caName = toStr(columns[0]);
            final String type = toStr(columns[1]);
            final String date = ISO_DATE_FORMAT.print(new DateTime(columns[2]));
            final String summary = toStr(columns[3]);
            if (updateRoaConf.equals(type)) {
                final Matcher m = updateConfPattern.matcher(summary);
                if (m.matches()) {
                    final String additions = m.group(1);
                    final String deletions = m.group(2);
                    result.add(new CaStatRoaEvent(caName, date, countRoas(additions), countRoas(deletions)));
                }
            } else if (createRoaSpec.equals(type)) {
                final Matcher m = createSpecPattern.matcher(summary);
                if (m.matches()) {
                    final String additions = m.group(1);
                    int added = countRoasUpdateSpecPattern(additions);
                    if (added > 0)
                        result.add(new CaStatRoaEvent(caName, date, added, 0));
                }
            } else if (deleteRoaSpec.equals(type)) {
                final Matcher m = deleteSpecPattern.matcher(summary);
                if (m.matches()) {
                    final String deletions = m.group(1);
                    int deleted = countRoasUpdateSpecPattern(deletions);
                    if (deleted > 0)
                        result.add(new CaStatRoaEvent(caName, date, 0, deleted));
                }
            } else if (activateCaSpec.equals(type) || activateNonHostedCaSpec.equals(type)) {
                result.add(CaStatCaEvent.created(caName, date));
            } else if (deleteCaSpec.equals(type) || deleteNonHostedCaSpec.equals(type)) {
                result.add(CaStatCaEvent.deleted(date));
            }
        }
        return result;
    }

    @Override
    public Collection<ManagedCertificateAuthority> findAllWithOutdatedManifests(DateTime nextUpdateCutoff, int maxResults) {
        return manager.createQuery(
            "SELECT ca" +
                "  FROM " + ManagedCertificateAuthority.class.getSimpleName() + " ca" +
                // Certificate authority configuration was updated since last check, so publish might be needed
                " WHERE ca.manifestAndCrlCheckNeeded = TRUE" +
                "    OR EXISTS (SELECT kp" +
                "                 FROM ca.keyPairs kp" +
                "                 JOIN kp.incomingResourceCertificate incoming" +
                // Key pair must be publishable and must have a current incoming certificate
                "                WHERE kp.status IN (:publishable)" +
                // Active objects that are not on the manifest, or inactive objects that are on the manifest, so publish needed
                "                  AND (       EXISTS (SELECT po" +
                "                                        FROM PublishedObject po" +
                "                                       WHERE po.issuingKeyPair = kp" +
                "                                         AND po.includedInManifest = TRUE" +
                "                                         AND (   (po.containingManifest IS NULL AND po.status in :active)" +
                "                                              OR (po.containingManifest IS NOT NULL AND po.status in :inactive)))" +
                // No active manifest, or manifest will expire soon, so publish needed
                "                       OR NOT EXISTS (SELECT mft" +
                "                                        FROM ManifestEntity mft" +
                "                                        JOIN mft.publishedObject po" +
                "                                       WHERE mft.keyPair = kp" +
                "                                         AND po.status IN :active" +
                "                                         AND po.validityPeriod.notValidAfter > :nextUpdateCutoff)" +
                // No active CRL, or CRL will expire soon, so publish needed
                "                       OR NOT EXISTS (SELECT crl" +
                "                                        FROM CrlEntity crl" +
                "                                        JOIN crl.publishedObject po" +
                "                                       WHERE crl.keyPair = kp" +
                "                                         AND po.status IN :active" +
                "                                         AND po.validityPeriod.notValidAfter > :nextUpdateCutoff)))",
            ManagedCertificateAuthority.class)
            // See KeyPairEntity.isPublishable for the next two parameters
            .setParameter("publishable", Arrays.asList(KeyPairStatus.PENDING, KeyPairStatus.CURRENT, KeyPairStatus.OLD))
            // Need to update when there are published object with pending status
            .setParameter("active", PublicationStatus.ACTIVE_STATUSES)
            .setParameter("inactive", EnumSet.complementOf(PublicationStatus.ACTIVE_STATUSES))
            .setParameter("nextUpdateCutoff", nextUpdateCutoff)
            .setMaxResults(maxResults)
            .getResultList();
    }

    @Override
    public List<ManagedCertificateAuthority> findAllWithManifestsExpiringBefore(DateTime notValidAfterCutoff, int maxResult) {
        return manager.createQuery(
                "SELECT DISTINCT ca, MIN(po.validityPeriod.notValidAfter) " +
                    "  FROM ManagedCertificateAuthority ca" +
                    "  JOIN ca.keyPairs kp," +
                    "       ManifestEntity mft" +
                    "  JOIN mft.publishedObject po" +
                    "  JOIN mft.certificate crt" +
                    " WHERE kp.status IN :publishable" +
                    "   AND crt.signingKeyPair = kp" +
                    "   AND po.validityPeriod.notValidAfter < :notValidAfterCutoff" +
                    " GROUP BY ca" +
                    " ORDER BY MIN(po.validityPeriod.notValidAfter) ASC",
                Object[].class)
            // See KeyPairEntity.isPublishable for the next two parameters
            .setParameter("publishable", Arrays.asList(KeyPairStatus.PENDING, KeyPairStatus.CURRENT, KeyPairStatus.OLD))
            .setParameter("notValidAfterCutoff", notValidAfterCutoff)
            .setMaxResults(maxResult)
            .getResultStream()
            .map(row -> (ManagedCertificateAuthority) row[0])
            .collect(Collectors.toList());
    }

    @Override
    public int deleteNonHostedPublicKeysWithoutSigningCertificates() {
        return createQuery("DELETE FROM PublicKeyEntity pk WHERE pk.outgoingResourceCertificates IS EMPTY")
            .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<ManagedCertificateAuthority> getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear() {
        // for context: deleting a CA is a USER command
        final Query sql = manager.createQuery(
            "SELECT ca FROM ManagedCertificateAuthority ca " +
                "WHERE ca.keyPairs IS EMPTY " +
                "AND NOT EXISTS (" +
                "   SELECT cau FROM CommandAudit cau " +
                "   WHERE cau.certificateAuthorityId = ca.id" +
                "   AND cau.commandGroup = :user " +
                "   AND cau.executionTime > :threshold " +
                ") " +
                "AND NOT EXISTS (" +
                "   SELECT rc FROM RoaConfiguration rc " +
                "   WHERE rc.certificateAuthority = ca " +
                "   AND rc.prefixes IS NOT EMPTY" +
                ") " +
                "AND NOT EXISTS (" +
                "   SELECT rac FROM RoaAlertConfiguration rac " +
                "   WHERE rac.certificateAuthority = ca" +
                ")");
        final DateTime yearAgo = new DateTime().minus(Duration.standardDays(366));
        return sql
            .setParameter("threshold", yearAgo)
            .setParameter("user", "USER")
            .getResultList();
    }

    private static String inClause(final Collection<String> items) {
        return items.stream().collect(Collectors.joining(",", "'", "'"));
    }

    private static int countRoasUpdateSpecPattern(String summary) {
        int c = summary.replaceFirst("\\[asn=AS[0-9]+, ", "").split("maximumLength=").length - 1;
        return Math.max(c, 0);
    }

    private static int countRoas(String summaryArray) {
        if (summaryArray != null && !"none".equals(summaryArray)) {
            return summaryArray.split("\\], \\[").length;
        }
        return 0;
    }

    private static String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    private static int toInt(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }
}

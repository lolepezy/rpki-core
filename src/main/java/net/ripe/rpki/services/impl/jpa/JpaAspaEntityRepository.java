package net.ripe.rpki.services.impl.jpa;

import lombok.NonNull;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.aspa.AspaEntity;
import net.ripe.rpki.domain.aspa.AspaEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaAspaEntityRepository extends JpaRepository<AspaEntity> implements AspaEntityRepository {

    @Override
    protected Class<AspaEntity> getEntityClass() {
        return AspaEntity.class;
    }

    @Override
    public List<AspaEntity> findByCertificateSigningKeyPair(@NonNull KeyPairEntity certificateSigningKeyPair) {
        return manager
            .createQuery("FROM AspaEntity WHERE certificate.signingKeyPair = :cskp", AspaEntity.class)
            .setParameter("cskp", certificateSigningKeyPair)
            .getResultList();
    }

    @Override
    public int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair) {
        return manager
            .createQuery("DELETE FROM AspaEntity WHERE certificate_id IN (SELECT id FROM OutgoingResourceCertificate orc WHERE orc.signingKeyPair = :cskp)")
            .setParameter("cskp", certificateSigningKeyPair)
            .executeUpdate();
    }
}

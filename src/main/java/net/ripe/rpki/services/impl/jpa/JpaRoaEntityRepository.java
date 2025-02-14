package net.ripe.rpki.services.impl.jpa;

import lombok.NonNull;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaRoaEntityRepository extends JpaRepository<RoaEntity> implements RoaEntityRepository {

    @Override
    protected Class<RoaEntity> getEntityClass() {
        return RoaEntity.class;
    }

    @Override
    public List<RoaEntity> findByCertificateSigningKeyPair(@NonNull KeyPairEntity certificateSigningKeyPair) {
        return manager
            .createQuery("FROM RoaEntity WHERE certificate.signingKeyPair = :cskp", RoaEntity.class)
            .setParameter("cskp", certificateSigningKeyPair)
            .getResultList();
    }

    @Override
    public int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair) {
        return manager
            .createQuery("DELETE FROM RoaEntity WHERE certificate_id IN (SELECT id FROM OutgoingResourceCertificate orc WHERE orc.signingKeyPair = :cskp)")
            .setParameter("cskp", certificateSigningKeyPair)
            .executeUpdate();
    }
}

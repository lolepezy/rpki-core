package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.ripencc.support.persistence.Repository;

import java.util.List;

public interface AspaEntityRepository extends Repository<AspaEntity> {

    List<AspaEntity> findByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair);

    int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair);
}

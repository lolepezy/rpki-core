package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;

public interface AspaEntityService {

    void aspaConfigurationUpdated(ManagedCertificateAuthority ca);

}

package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Collection;

import static net.ripe.rpki.services.impl.background.BackgroundServices.CA_CLEAN_UP_SERVICE;

@Service(CA_CLEAN_UP_SERVICE)
public class CaCleanUpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final CommandService commandService;
    private final Counter deletedCasWithoutKeyPairsCounter;
    private final RoaViewService roaViewService;
    @Getter
    private final boolean enabled;

    @Inject
    public CaCleanUpServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                CertificateAuthorityRepository certificateAuthorityRepository,
                                CommandService commandService,
                                RoaViewService roaViewService,
                                MeterRegistry meterRegistry,
                                @Value("${certificate.authority.cleanup.service.enabled:false}") boolean enabled) {
        super(backgroundTaskRunner);
        this.enabled = enabled;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.commandService = commandService;
        this.roaViewService = roaViewService;

        this.deletedCasWithoutKeyPairsCounter = Counter.builder("rpkicore.deleted.ca.without.key.pairs")
            .description("The number of deleted CAs without active key pairs")
            .register(meterRegistry);
    }

    @Override
    public boolean isActive() {
        return enabled;
    }

    @Override
    protected void runService() {
        if (enabled) {
            final Collection<ManagedCertificateAuthority> casToDelete = certificateAuthorityRepository.getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear();
            deletedCasWithoutKeyPairsCounter.increment(casToDelete.size());
            casToDelete.forEach(ca -> {
                final RoaConfigurationData roaConfiguration = roaViewService.getRoaConfiguration(ca.getId());
                commandService.execute(new DeleteCertificateAuthorityCommand(ca.getVersionedId(), ca.getName(), roaConfiguration));
            });
            log.info("Deleted {} CAs without active key pair for more than a year", casToDelete.size());
        } else {
            log.warn("The service {} is disabled.", getName());
        }
    }

    @Override
    public String getName() {
        return "Cleanup CAs without activity and key pairs";
    }
}

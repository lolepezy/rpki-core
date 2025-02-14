package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("resourceCacheUpdateService")
public class ResourceCacheUpdateServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final ResourceCacheService resourceCacheService;

    @Autowired
    public ResourceCacheUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                          ResourceCacheService resourceCacheService) {
        super(backgroundTaskRunner);
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    protected void runService() {
        resourceCacheService.updateFullResourceCache();
    }
}

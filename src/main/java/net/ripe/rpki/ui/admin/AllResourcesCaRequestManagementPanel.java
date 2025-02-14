package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class AllResourcesCaRequestManagementPanel extends Panel {

    private static final long serialVersionUID = 1L;

    @SpringBean
    private RepositoryConfiguration repositoryConfiguration;

    @SpringBean
    private CertificateAuthorityViewService caViewService;

    @SpringBean
    private CommandService commandService;

    public AllResourcesCaRequestManagementPanel(String id) {
        super(id);


        final ManagedCertificateAuthorityData allResourcesCA  = (ManagedCertificateAuthorityData) caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
        if(allResourcesCA != null) {
            add(new AllResourcesCaManagementPanel("managementPanel", allResourcesCA));
        }

        add(new Link<Object>("signRequest") {
            private static final long serialVersionUID1 = 1L;

            @Override
            public void onClick() {
                final CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
                commandService.execute(new AllResourcesCaResourcesCommand(allResourcesCa.getVersionedId()));
                setResponsePage(UpstreamCaManagementPage.class);
            }
        });


    }
}


package net.ripe.rpki.server.api.services.command;

/**
 * This exception indicates that a roa config update commands is trying to add private ASNs.
 */
public class EntityTagDoesNotMatchException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public EntityTagDoesNotMatchException(String expectedEntitytag, String actualEntityTag) {
        super("specified entity tag " + actualEntityTag + " does not match expected entity tag " + expectedEntitytag);
    }
}

package edu.harvard.iq.dataverse.pidproviders.did;

import edu.harvard.iq.dataverse.GlobalId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DidPidProviderTest {

    @Test
    public void testParsePersistentId() {
        DidPidProvider provider = new DidPidProvider("did-provider", "DID", "web:localhost", "", "randomString",
                "DEPENDENT", "", "", null);

        String didString = "did:web:localhost:abc-123";
        GlobalId globalId = provider.parsePersistentId(didString);

        assertNotNull(globalId);
        assertEquals("did", globalId.getProtocol());
        assertEquals("web:localhost", globalId.getAuthority());
        assertEquals("abc-123", globalId.getIdentifier());
        assertEquals(":", globalId.getSeparator());
    }

    @Test
    public void testGetUrlPrefix() {
        DidPidProvider provider = new DidPidProvider("did-provider", "DID", "web:localhost", "", "randomString",
                "DEPENDENT", "", "", null);
        assertEquals("https://dev.uniresolver.io/#did:", provider.getUrlPrefix());
    }
}

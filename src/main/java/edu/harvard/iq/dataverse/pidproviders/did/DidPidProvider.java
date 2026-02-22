package edu.harvard.iq.dataverse.pidproviders.did;

import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.GlobalId;
import edu.harvard.iq.dataverse.pidproviders.AbstractPidProvider;
import edu.harvard.iq.dataverse.settings.JvmSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;

public class DidPidProvider extends AbstractPidProvider {

    public static final String TYPE = "DID";
    public static final String DID_PROTOCOL = "did";
    public static final String DID_RESOLVER_URL = "https://dev.uniresolver.io/#did:";

    private static final Logger logger = Logger.getLogger(DidPidProvider.class.getCanonicalName());
    private String apiUrl;
    protected EntityManager em;

    public DidPidProvider(String id, String label, String providerAuthority, String providerShoulder,
            String identifierGenerationStyle,
            String datafilePidFormat, String managedList, String excludedList,
            EntityManager em) {
        super(id, label, DID_PROTOCOL, providerAuthority, providerShoulder, identifierGenerationStyle,
                datafilePidFormat, managedList, excludedList);
        this.em = em;
    }

    @Override
    public String getSeparator() {
        return ":";
    }

    @Override
    public String getUrlPrefix() {
        return DID_RESOLVER_URL;
    }

    @Override
    protected GlobalId parsePersistentId(String protocol, String identifierString) {
        if (!DID_PROTOCOL.equals(protocol) || identifierString == null) {
            return null;
        }

        String separator = getSeparator();
        // Be lenient: if it contains a separator, treat as authority:identifier
        if (identifierString.contains(separator)) {
            String[] parts = identifierString.split(separator, 2);
            logger.fine("Leniently parsed DID: protocol=" + protocol + ", authority=" + parts[0] + ", identifier="
                    + parts[1]);
            return super.parsePersistentId(protocol, parts[0], parts[1]);
        }

        // Fallback to current authority if no separator
        return super.parsePersistentId(protocol, getAuthority(), identifierString);
    }

    @Override
    public boolean alreadyRegistered(GlobalId globalId, boolean noProviderDefault) throws Exception {
        // This is called when we only have a GlobalId (e.g. searching).
        // For DIDs, we check local uniqueness.
        boolean existsLocally = !pidProviderService.isGlobalIdLocallyUnique(globalId);
        return existsLocally ? existsLocally : noProviderDefault;
    }

    @Override
    public boolean alreadyRegistered(DvObject dvo) throws Exception {
        // During publication, we check if ANY OTHER object has this GlobalId.
        // This prevents the "Reserving PID failed" error when it's just ourselves.
        return !pidProviderService.isGlobalIdLocallyUnique(dvo.getGlobalId(), dvo);
    }

    @Override
    public boolean registerWhenPublished() {
        return false;
    }

    @Override
    public List<String> getProviderInformation() {
        return List.of(getId(), DID_RESOLVER_URL);
    }

    @Override
    public String createIdentifier(DvObject dvObject) throws Throwable {
        if (dvObject.getIdentifier() == null || dvObject.getIdentifier().isEmpty()) {
            dvObject = generatePid(dvObject);
        }

        try {
            String registeredDid = registerDidWithOdrl(dvObject);
            if (registeredDid != null && !registeredDid.isEmpty()) {
                logger.info("ODRL API returned DID during createIdentifier: " + registeredDid);
                updateDvObjectWithRegisteredDid(dvObject, registeredDid);
                return dvObject.getIdentifier();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to register DID with ODRL API during creation: " + e.getMessage(), e);
        }

        return dvObject.getIdentifier();
    }

    @Override
    public Map<String, String> getIdentifierMetadata(DvObject dvo) {
        return new HashMap<>();
    }

    @Override
    public String modifyIdentifierTargetURL(DvObject dvo) throws Exception {
        return "didModifyIdentifierTargetURL";
    }

    @Override
    public void deleteIdentifier(DvObject dvo) throws Exception {
        // no-op
    }

    @Override
    public boolean publicizeIdentifier(DvObject dvObject) {
        logger.info("Publicizing identifier for " + dvObject.getClass().getSimpleName() + " " + dvObject.getId()
                + ". Current GlobalID: " + dvObject.getGlobalId().asString());
        if (dvObject.getGlobalId() == null) {
            generatePid(dvObject);
        }

        try {
            String registeredDid = registerDidWithOdrl(dvObject);
            if (registeredDid != null && !registeredDid.isEmpty()) {
                logger.info("ODRL API returned DID: " + registeredDid);
                // Parse the returned DID and update the dvObject
                updateDvObjectWithRegisteredDid(dvObject, registeredDid);
                logger.info("Updated GlobalID after ODRL registration: " + dvObject.getGlobalId().asString());
                return true;
            }
            logger.warning("ODRL registration returned empty DID");
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register DID with ODRL API: " + e.getMessage(), e);
            return false;
        }
    }

    private String registerDidWithOdrl(DvObject dvObject) {
        String effectiveApiUrl = JvmSettings.DID_API_URL.lookupOptional(getId()).orElse("https://odrl.dev.codata.org");
        if (effectiveApiUrl == null || effectiveApiUrl.isEmpty()) {
            logger.warning("ODRL API URL not configured, skipping registration");
            return "";
        }

        Client client = ClientBuilder.newClient();
        try {
            // Prepare payload
            JsonObject payload = Json.createObjectBuilder()
                    .add("title", dvObject.getDisplayName())
                    .add("type", dvObject.getClass().getSimpleName().toLowerCase())
                    .add("dataverse_url", JvmSettings.SITE_URL.lookupOptional().orElse(""))
                    .build();

            JsonObject requestBody = Json.createObjectBuilder()
                    .add("payload", payload)
                    .build();

            // The apiUrl already contains the full path from JVM options in run-did.sh
            logger.info("Calling ODRL API at: " + effectiveApiUrl);
            Response response = client.target(effectiveApiUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(requestBody, MediaType.APPLICATION_JSON));

            if (response.getStatus() == Response.Status.OK.getStatusCode() ||
                    response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                JsonObject result = response.readEntity(JsonObject.class);
                logger.info("ODRL API full response: " + result.toString());

                // Try "did" first, then "id" as fallback
                String registeredDid = result.getString("did", result.getString("id", ""));
                if (registeredDid.isEmpty()) {
                    // One more try: look inside a "data" or "result" object if it exists
                    if (result.containsKey("data") && result.get("data") instanceof JsonObject) {
                        JsonObject data = result.getJsonObject("data");
                        registeredDid = data.getString("did", data.getString("id", ""));
                    }
                }

                logger.info("Successfully registered/retrieved DID: " + registeredDid);
                return registeredDid;
            } else {
                String error = response.readEntity(String.class);
                logger.severe("ODRL API returned error " + response.getStatus() + ": " + error);
                return "";
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calling ODRL API", e);
            return "";
        } finally {
            client.close();
        }
    }

    private void updateDvObjectWithRegisteredDid(DvObject dvo, String registeredDid) {
        // registeredDid format: did:method:identifier
        logger.info("Parsing registered DID: " + registeredDid);
        if (registeredDid != null && registeredDid.startsWith("did:")) {
            String[] parts = registeredDid.split(":", 3);
            if (parts.length >= 3) {
                logger.info("Updating DvObject PID parts: protocol=" + parts[0] + ", authority=" + parts[1]
                        + ", identifier=" + parts[2]);
                dvo.setProtocol(parts[0]);
                dvo.setAuthority(parts[1]);
                dvo.setIdentifier(parts[2]);
                if (em != null) {
                    em.merge(dvo);
                    logger.info("Explicitly merged DvObject with ODRL DID: " + dvo.getGlobalId().asString());
                } else {
                    logger.warning("EntityManager is null, could not explicitly merge ODRL DID: "
                            + dvo.getGlobalId().asString());
                }
            } else {
                logger.warning("Returned DID format has less than 3 parts: " + registeredDid);
            }
        } else if (registeredDid != null && registeredDid.contains(":")) {
            // Fallback for oyd:identifier if "did:" is missing but we want to stick to
            // did:oyd
            String[] parts = registeredDid.split(":", 2);
            logger.info("Fallback parsing for non-did prefix: " + registeredDid);
            dvo.setProtocol(DID_PROTOCOL);
            dvo.setAuthority(parts[0]);
            dvo.setIdentifier(parts[1]);
            if (em != null) {
                em.merge(dvo);
                logger.info("Explicitly merged DvObject with ODRL DID (fallback): " + dvo.getGlobalId().asString());
            } else {
                logger.warning("EntityManager is null, could not explicitly merge ODRL DID (fallback): "
                        + dvo.getGlobalId().asString());
            }
        } else {
            logger.warning("Returned DID format invalid: " + registeredDid);
        }
    }

    @Override
    public String getProviderType() {
        return TYPE;
    }
}

package edu.harvard.iq.dataverse.pidproviders.did;

import edu.harvard.iq.dataverse.pidproviders.PidProvider;
import edu.harvard.iq.dataverse.pidproviders.PidProviderFactory;
import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class DidPidProviderFactory implements PidProviderFactory {

    @Override
    public String getType() {
        return DidPidProvider.TYPE;
    }

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Override
    public PidProvider createPidProvider(String id, DvObjectServiceBean dvObjectService) {
        String label = JvmSettings.PID_PROVIDER_LABEL.lookupOptional(id).orElse("DID");
        String authority = JvmSettings.PID_PROVIDER_AUTHORITY.lookupOptional(id).orElse("web:localhost%3A8080");
        String shoulder = JvmSettings.PID_PROVIDER_SHOULDER.lookupOptional(id).orElse("");
        String identifierGenerationStyle = JvmSettings.PID_PROVIDER_IDENTIFIER_GENERATION_STYLE.lookupOptional(id)
                .orElse("randomString");
        String datafilePidFormat = JvmSettings.PID_PROVIDER_DATAFILE_PID_FORMAT.lookupOptional(id).orElse("DEPENDENT");
        String managedList = JvmSettings.PID_PROVIDER_MANAGED_LIST.lookupOptional(id).orElse("");
        String excludedList = JvmSettings.PID_PROVIDER_EXCLUDED_LIST.lookupOptional(id).orElse("");
        String apiUrl = JvmSettings.DID_API_URL.lookupOptional(id).orElse("https://odrl.dev.codata.org");

        return new DidPidProvider(id, label, authority, shoulder, identifierGenerationStyle, datafilePidFormat,
                managedList, excludedList, em);
    }
}

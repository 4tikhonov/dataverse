package edu.harvard.iq.dataverse.pidproviders;

import edu.harvard.iq.dataverse.DvObjectServiceBean;

public interface PidProviderFactory {

    String getType();

    PidProvider createPidProvider(String id, DvObjectServiceBean dvObjectService);
}

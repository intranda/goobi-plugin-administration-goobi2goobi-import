package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class LdapConfigurationItem {

    private String oldLadapName;

    private String newLdapName;

    private String homeDirectory;
    private String gidNumber;
    private String dn;
    private String objectClass;
    private String sambaSID;
    private String sn;
    private String uid;
    private String description;
    private String displayName;
    private String gecos;
    private String loginShell;
    private String sambaAcctFlags;
    private String sambaLogonScript;
    private String sambaPwdMustChange;
    private String sambaPasswordHistory;
    private String sambaPrimaryGroupSID;
    private String sambaLogonHours;
    private String sambaKickoffTime;

}

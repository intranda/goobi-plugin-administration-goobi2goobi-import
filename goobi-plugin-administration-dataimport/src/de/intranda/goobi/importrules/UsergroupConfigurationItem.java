package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class UsergroupConfigurationItem {


    private String oldUsergroupName;
    private String newUsergroupName;

    private List<String> addRoleList = new ArrayList<>();
    private List<String> removeRoleList = new ArrayList<>();
}

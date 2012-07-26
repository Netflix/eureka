package com.netflix.discovery;

public enum Version {
    V1, V2;

    public static Version toEnum(String v){
        for(Version version : Version.values()){
            if(version.name().equalsIgnoreCase(v)){
                return version;
            }
        }
        //Defaults to v2
        return V2;
    }
}

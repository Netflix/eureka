package com.netflix.discovery;

import com.netflix.discovery.shared.Applications;

public interface BackupRegistry {
    
    // Fast fail or not
    Applications fetchRegistry(); 

}

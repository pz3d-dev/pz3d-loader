package com.pavelvoronin.pz3dLoader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LoaderStatusTest {
    @Test void retainedClient(){
        assertEquals("READY",LoaderUi.displayStatus("RESTART","READY",true));
        assertEquals("RESTART",LoaderUi.displayStatus("RESTART","READY",false));
        assertEquals("RESTART",LoaderUi.displayStatus("RESTART","STARTING",true));
    }
    @Test void failuresAndDisabledStayVisible(){
        for(String state:new String[]{"FAILED","DISABLED","MISSING","INCOMPATIBLE","LEGACY"})
            assertEquals(state,LoaderUi.displayStatus(state,"READY",true));
        assertEquals("FAILED",LoaderUi.displayStatus("RESTART","FAILED",true));
    }
}

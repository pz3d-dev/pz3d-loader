package com.pavelvoronin.pz3dLoader;

import org.junit.jupiter.api.Test;

final class LoaderVerificationTest {
    @Test void loadingAndSignatures() throws Exception { LoaderTests.main(new String[0]); }
    @Test void signedUpdates() throws Exception { UpdateTests.main(new String[0]); }
}

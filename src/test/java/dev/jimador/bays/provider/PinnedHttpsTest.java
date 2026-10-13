package dev.jimador.bays.provider;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetAddress;
import java.net.URI;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PinnedHttpsTest {
    @Test void rejectsPrivateAndSpecialAddressesBeforeConnecting() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.0.0.8", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.100.100.100", "198.18.0.1", "[fc00::1]", "[::1]"}) {
            assertFalse(PinnedHttps.publicAddress(InetAddress.getByName(ip)), ip);
            assertThrows(IOException.class, () -> PinnedHttps.validatedAddress(URI.create("https://" + ip + "/")), ip);
        }
        assertTrue(PinnedHttps.publicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(PinnedHttps.publicAddress(InetAddress.getByName("198.185.159.144")));
    }
    @Test void rejectsUnsafeUrlForms() {
        for (String url : new String[]{"http://example.org/", "https://user@example.org/",
                "https://example.org:8443/", "https://localhost/", "https://host.local/",
                "https://example.org/#fragment"})
            assertThrows(IOException.class, () -> PinnedHttps.validatedAddress(URI.create(url)), url);
    }
}

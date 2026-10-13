package dev.jimador.bays.provider;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Fetch an untrusted HTTPS venue URL through a validated, pinned public address. */
final class PinnedHttps {
    record Page(int status, String contentType, String body) {}
    private PinnedHttps() {}
    static Page get(URI uri, int maxBytes) throws IOException {
        InetAddress pinned = validatedAddress(uri);
        String pinnedText = pinned instanceof Inet6Address ? "[" + pinned.getHostAddress() + "]"
                : pinned.getHostAddress();
        Path body = Files.createTempFile("bayes-venue-", ".html");
        try {
            var process = new ProcessBuilder("curl", "-q", "--silent", "--show-error",
                    "--noproxy", "*", "--proto", "=https", "--max-redirs", "0",
                    "--connect-timeout", "5", "--max-time", "8", "--max-filesize", Integer.toString(maxBytes),
                    "--resolve", uri.getHost() + ":443:" + pinnedText,
                    "--output", body.toString(), "--write-out", "%{http_code}\\n%{content_type}",
                    uri.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] output = process.getInputStream().readNBytes(512);
            boolean ended;
            try { ended = process.waitFor(Duration.ofSeconds(9).toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); process.destroyForcibly();
                throw new IOException("Venue fetch interrupted", interrupted);
            }
            if (!ended) { process.destroyForcibly(); throw new IOException("Venue fetch timed out"); }
            if (process.exitValue() != 0) throw new IOException("Venue fetch failed");
            String[] metadata = new String(output, StandardCharsets.UTF_8).split("\\n", 2);
            if (metadata.length != 2 || !metadata[0].matches("[0-9]{3}"))
                throw new IOException("Venue response metadata was invalid");
            int status = Integer.parseInt(metadata[0]);
            String contentType = metadata[1].trim();
            if (status != 200 || !contentType.toLowerCase(Locale.ROOT).contains("text/html"))
                return new Page(status, contentType, "");
            if (Files.size(body) > maxBytes) throw new IOException("Venue page exceeds size limit");
            return new Page(status, contentType, Files.readString(body, StandardCharsets.UTF_8));
        } finally { Files.deleteIfExists(body); }
    }
    static InetAddress validatedAddress(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getHost().length() > 100
                || uri.getRawFragment() != null) throw new IOException("Unsafe venue URL");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.matches("[a-z0-9.-]+") || !host.contains(".") || host.matches("[0-9.]+")
                || host.endsWith(".") || host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".invalid")
                || host.endsWith(".test")) throw new IOException("Private or invalid venue host");
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IOException("Venue host has no address");
        for (InetAddress address : addresses) if (!publicAddress(address))
            throw new IOException("Venue host resolves outside the public internet");
        for (InetAddress address : addresses) if (address instanceof Inet4Address) return address;
        return addresses[0];
    }
    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && c >= 64 && c <= 127)
                    && !(a == 169 && c == 254)
                    && !(a == 172 && c >= 16 && c <= 31)
                    && !(a == 192 && ((c == 0 && (d == 0 || d == 2)) || c == 168))
                    && !(a == 198 && ((c == 18 || c == 19) || (c == 51 && d == 100)));
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 255;
            return (first & 0xfe) != 0xfc && first != 0 && first != 0xff;
        }
        return false;
    }
}

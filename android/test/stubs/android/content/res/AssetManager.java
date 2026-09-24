package android.content.res;
import java.io.*;
public class AssetManager {
    private final byte[] content;
    public AssetManager(byte[] content) { this.content = content; }
    public InputStream open(String name) throws IOException { return new ByteArrayInputStream(content); }
}

package android.content;
import android.content.res.AssetManager;
public class Context {
    private final AssetManager assets;
    public Context(byte[] assetContent) { this.assets = new AssetManager(assetContent); }
    public AssetManager getAssets() { return assets; }
}

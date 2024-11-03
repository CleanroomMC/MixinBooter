package zone.rong.mixinbooter.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;

import java.io.IOException;

public class MockedArtifactVersionAdapter extends TypeAdapter<ArtifactVersion> {

    @Override
    public void write(JsonWriter out, ArtifactVersion value) { }

    @Override
    public ArtifactVersion read(JsonReader in) throws IOException {
        in.nextString();
        return new MockedArtifactVersion();
    }

    private static class MockedArtifactVersion implements ArtifactVersion {

        @Override
        public String getLabel() {
            return "";
        }

        @Override
        public String getVersionString() {
            return "";
        }

        @Override
        public boolean containsVersion(ArtifactVersion source) {
            return false;
        }

        @Override
        public String getRangeString() {
            return "";
        }

        @Override
        public int compareTo(ArtifactVersion o) {
            return 0;
        }

    }

}

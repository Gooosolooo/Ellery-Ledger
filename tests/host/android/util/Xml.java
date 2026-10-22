package android.util;

import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

/** 仅供主机测试：使用与 Android 相同接口的 XML pull 解析器。不会编入 App。 */
public final class Xml {
    private Xml() { }

    public static XmlPullParser newPullParser() {
        return new KXmlParser();
    }
}

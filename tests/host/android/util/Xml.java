package android.util;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.xmlpull.v1.XmlPullParser;

/** 仅供主机测试，用 JDK StAX 驱动生产导入逻辑；不会打包到 App。 */
public final class Xml {
    private Xml() { }

    public static XmlPullParser newPullParser() {
        XMLStreamReader[] reader = new XMLStreamReader[1];
        int[] event = {XmlPullParser.START_DOCUMENT};
        return (XmlPullParser) Proxy.newProxyInstance(XmlPullParser.class.getClassLoader(),
                new Class<?>[]{XmlPullParser.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "setFeature":
                            return null;
                        case "setInput":
                            XMLInputFactory factory = XMLInputFactory.newFactory();
                            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
                            reader[0] = factory.createXMLStreamReader((InputStream) arguments[0], (String) arguments[1]);
                            event[0] = XmlPullParser.START_DOCUMENT;
                            return null;
                        case "getEventType":
                            return event[0];
                        case "getName":
                            return reader[0].hasName() ? reader[0].getLocalName() : null;
                        case "getAttributeValue":
                            return reader[0].getAttributeValue((String) arguments[0], (String) arguments[1]);
                        case "nextText":
                            String text = reader[0].getElementText();
                            event[0] = XmlPullParser.END_TAG;
                            return text;
                        case "next":
                            while (reader[0].hasNext()) {
                                int next = reader[0].next();
                                if (next == XMLStreamConstants.START_ELEMENT) {
                                    event[0] = XmlPullParser.START_TAG;
                                    return event[0];
                                }
                                if (next == XMLStreamConstants.END_ELEMENT) {
                                    event[0] = XmlPullParser.END_TAG;
                                    return event[0];
                                }
                                if (next == XMLStreamConstants.CHARACTERS || next == XMLStreamConstants.CDATA) {
                                    event[0] = XmlPullParser.TEXT;
                                    return event[0];
                                }
                            }
                            event[0] = XmlPullParser.END_DOCUMENT;
                            return event[0];
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.validator.routines;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.validator.routines.DomainValidator.ArrayType;

import junit.framework.TestCase;

/**
 * Tests for the DomainValidator.
 *
 */
public class DomainValidatorTest extends TestCase {

    private DomainValidator validator;

    @Override
    public void setUp() {
        validator = DomainValidator.getInstance();
    }

    public void testValidDomains() {
        assertTrue("apache.org should validate", validator.isValid("apache.org"));
        assertTrue("www.google.com should validate", validator.isValid("www.google.com"));

        assertTrue("test-domain.com should validate", validator.isValid("test-domain.com"));
        assertTrue("test---domain.com should validate", validator.isValid("test---domain.com"));
        assertTrue("test-d-o-m-ain.com should validate", validator.isValid("test-d-o-m-ain.com"));
        assertTrue("two-letter domain label should validate", validator.isValid("as.uk"));

        assertTrue("case-insensitive ApAchE.Org should validate", validator.isValid("ApAchE.Org"));

        assertTrue("single-character domain label should validate", validator.isValid("z.com"));

        assertTrue("i.have.an-example.domain.name should validate", validator.isValid("i.have.an-example.domain.name"));
    }

    public void testInvalidDomains() {
        assertFalse("bare TLD .org shouldn't validate", validator.isValid(".org"));
        assertFalse("domain name with spaces shouldn't validate", validator.isValid(" apache.org "));
        assertFalse("domain name containing spaces shouldn't validate", validator.isValid("apa che.org"));
        assertFalse("domain name starting with dash shouldn't validate", validator.isValid("-testdomain.name"));
        assertFalse("domain name ending with dash shouldn't validate", validator.isValid("testdomain-.name"));
        assertFalse("domain name starting with multiple dashes shouldn't validate", validator.isValid("---c.com"));
        assertFalse("domain name ending with multiple dashes shouldn't validate", validator.isValid("c--.com"));
        assertFalse("domain name with invalid TLD shouldn't validate", validator.isValid("apache.rog"));

        assertFalse("URL shouldn't validate", validator.isValid("http://www.apache.org"));
        assertFalse("Empty string shouldn't validate as domain name", validator.isValid(" "));
        assertFalse("Null shouldn't validate as domain name", validator.isValid(null));
    }

    public void testTopLevelDomains() {
        // infrastructure TLDs
        assertTrue(".arpa should validate as iTLD", validator.isValidInfrastructureTld(".arpa"));
        assertFalse(".com shouldn't validate as iTLD", validator.isValidInfrastructureTld(".com"));

        // generic TLDs
        assertTrue(".name should validate as gTLD", validator.isValidGenericTld(".name"));
        assertFalse(".us shouldn't validate as gTLD", validator.isValidGenericTld(".us"));

        // country code TLDs
        assertTrue(".uk should validate as ccTLD", validator.isValidCountryCodeTld(".uk"));
        assertFalse(".org shouldn't validate as ccTLD", validator.isValidCountryCodeTld(".org"));

        // case-insensitive
        assertTrue(".COM should validate as TLD", validator.isValidTld(".COM"));
        assertTrue(".BiZ should validate as TLD", validator.isValidTld(".BiZ"));

        // corner cases
        assertFalse("invalid TLD shouldn't validate", validator.isValid(".nope")); // TODO this is not guaranteed invalid forever
        assertFalse("empty string shouldn't validate as TLD", validator.isValid(""));
        assertFalse("null shouldn't validate as TLD", validator.isValid(null));
    }

    public void testAllowLocal() {
       final DomainValidator noLocal = DomainValidator.getInstance(false);
       final DomainValidator allowLocal = DomainValidator.getInstance(true);

       // Default is false, and should use singletons
       assertEquals(noLocal, validator);

       // Default won't allow local
       assertFalse("localhost.localdomain should validate", noLocal.isValid("localhost.localdomain"));
       assertFalse("localhost should validate", noLocal.isValid("localhost"));

       // But it may be requested
       assertTrue("localhost.localdomain should validate", allowLocal.isValid("localhost.localdomain"));
       assertTrue("localhost should validate", allowLocal.isValid("localhost"));
       assertTrue("hostname should validate", allowLocal.isValid("hostname"));
       assertTrue("machinename should validate", allowLocal.isValid("machinename"));

       // Check the localhost one with a few others
       assertTrue("apache.org should validate", allowLocal.isValid("apache.org"));
       assertFalse("domain name with spaces shouldn't validate", allowLocal.isValid(" apache.org "));
    }

    public void testIDN() {
       assertTrue("b\u00fccher.ch in IDN should validate", validator.isValid("www.xn--bcher-kva.ch"));
    }

    public void testIDNJava6OrLater() {
        final String version = System.getProperty("java.version");
        if (version.compareTo("1.6") < 0) {
            System.out.println("Cannot run Unicode IDN tests");
            return; // Cannot run the test
        } // xn--d1abbgf6aiiy.xn--p1ai http://президент.рф
       assertTrue("b\u00fccher.ch should validate", validator.isValid("www.b\u00fccher.ch"));
       assertTrue("xn--d1abbgf6aiiy.xn--p1ai should validate", validator.isValid("xn--d1abbgf6aiiy.xn--p1ai"));
       assertTrue("президент.рф should validate", validator.isValid("президент.рф"));
       assertFalse("www.\uFFFD.ch FFFD should fail", validator.isValid("www.\uFFFD.ch"));
    }

    // RFC2396: domainlabel   = alphanum | alphanum *( alphanum | "-" ) alphanum
    public void testRFC2396domainlabel() { // use fixed valid TLD
        assertTrue("a.ch should validate", validator.isValid("a.ch"));
        assertTrue("9.ch should validate", validator.isValid("9.ch"));
        assertTrue("az.ch should validate", validator.isValid("az.ch"));
        assertTrue("09.ch should validate", validator.isValid("09.ch"));
        assertTrue("9-1.ch should validate", validator.isValid("9-1.ch"));
        assertFalse("91-.ch should not validate", validator.isValid("91-.ch"));
        assertFalse("-.ch should not validate", validator.isValid("-.ch"));
    }

    // RFC2396 toplabel = alpha | alpha *( alphanum | "-" ) alphanum
    public void testRFC2396toplabel() {
        // These tests use non-existent TLDs so currently need to use a package protected method
        assertTrue("a.c (alpha) should validate", validator.isValidDomainSyntax("a.c"));
        assertTrue("a.cc (alpha alpha) should validate", validator.isValidDomainSyntax("a.cc"));
        assertTrue("a.c9 (alpha alphanum) should validate", validator.isValidDomainSyntax("a.c9"));
        assertTrue("a.c-9 (alpha - alphanum) should validate", validator.isValidDomainSyntax("a.c-9"));
        assertTrue("a.c-z (alpha - alpha) should validate", validator.isValidDomainSyntax("a.c-z"));

        assertFalse("a.9c (alphanum alpha) should fail", validator.isValidDomainSyntax("a.9c"));
        assertFalse("a.c- (alpha -) should fail", validator.isValidDomainSyntax("a.c-"));
        assertFalse("a.- (-) should fail", validator.isValidDomainSyntax("a.-"));
        assertFalse("a.-9 (- alphanum) should fail", validator.isValidDomainSyntax("a.-9"));
    }

    public void testDomainNoDots() {// rfc1123
        assertTrue("a (alpha) should validate", validator.isValidDomainSyntax("a"));
        assertTrue("9 (alphanum) should validate", validator.isValidDomainSyntax("9"));
        assertTrue("c-z (alpha - alpha) should validate", validator.isValidDomainSyntax("c-z"));

        assertFalse("c- (alpha -) should fail", validator.isValidDomainSyntax("c-"));
        assertFalse("-c (- alpha) should fail", validator.isValidDomainSyntax("-c"));
        assertFalse("- (-) should fail", validator.isValidDomainSyntax("-"));
    }

    public void testValidator297() {
        assertTrue("xn--d1abbgf6aiiy.xn--p1ai should validate", validator.isValid("xn--d1abbgf6aiiy.xn--p1ai")); // This uses a valid TLD
     }

    // labels are a max of 63 chars and domains 253
    public void testValidator306() {
        final String longString = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz0123456789A";
        assertEquals(63, longString.length()); // 26 * 2 + 11

        assertTrue("63 chars label should validate", validator.isValidDomainSyntax(longString+".com"));
        assertFalse("64 chars label should fail", validator.isValidDomainSyntax(longString+"x.com"));

        assertTrue("63 chars TLD should validate", validator.isValidDomainSyntax("test."+longString));
        assertFalse("64 chars TLD should fail", validator.isValidDomainSyntax("test.x"+longString));

        final String longDomain =
                longString
                + "." + longString
                + "." + longString
                + "." + longString.substring(0,61)
                ;
        assertEquals(253, longDomain.length());
        assertTrue("253 chars domain should validate", validator.isValidDomainSyntax(longDomain));
        assertFalse("254 chars domain should fail", validator.isValidDomainSyntax(longDomain+"x"));
    }

    // Check that IDN.toASCII behaves as it should (when wrapped by DomainValidator.unicodeToASCII)
    // Tests show that method incorrectly trims a trailing "." character
    public void testUnicodeToASCII() {
        final String[] asciidots = {
                "",
                ",",
                ".", // fails IDN.toASCII, but should pass wrapped version
                "a.", // ditto
                "a.b",
                "a..b",
                "a...b",
                ".a",
                "..a",
        };
        for(final String s : asciidots) {
            assertEquals(s,DomainValidator.unicodeToASCII(s));
        }
        // RFC3490 3.1. 1)
//      Whenever dots are used as label separators, the following
//      characters MUST be recognized as dots: U+002E (full stop), U+3002
//      (ideographic full stop), U+FF0E (fullwidth full stop), U+FF61
//      (halfwidth ideographic full stop).
        final String otherDots[][] = {
                {"b\u3002", "b.",},
                {"b\uFF0E", "b.",},
                {"b\uFF61", "b.",},
                {"\u3002", ".",},
                {"\uFF0E", ".",},
                {"\uFF61", ".",},
        };
        for(final String s[] : otherDots) {
            assertEquals(s[1],DomainValidator.unicodeToASCII(s[0]));
        }
    }

    // Check if IDN.toASCII is broken or not
    public void testIsIDNtoASCIIBroken() {
        System.out.println(">>DomainValidatorTest.testIsIDNtoASCIIBroken()");
        final String input = ".";
        final boolean ok = input.equals(IDN.toASCII(input));
        System.out.println("IDN.toASCII is " + (ok? "OK" : "BROKEN"));
        final String props[] = {
        "java.version", //    Java Runtime Environment version
        "java.vendor", // Java Runtime Environment vendor
        "java.vm.specification.version", //   Java Virtual Machine specification version
        "java.vm.specification.vendor", //    Java Virtual Machine specification vendor
        "java.vm.specification.name", //  Java Virtual Machine specification name
        "java.vm.version", // Java Virtual Machine implementation version
        "java.vm.vendor", //  Java Virtual Machine implementation vendor
        "java.vm.name", //    Java Virtual Machine implementation name
        "java.specification.version", //  Java Runtime Environment specification version
        "java.specification.vendor", //   Java Runtime Environment specification vendor
        "java.specification.name", // Java Runtime Environment specification name
        "java.class.version", //  Java class format version number
        };
        for(final String t : props) {
            System.out.println(t + "=" + System.getProperty(t));
        }
        System.out.println("<<DomainValidatorTest.testIsIDNtoASCIIBroken()");
        assertTrue(true); // dummy assertion to satisfy lint
    }

    // Check array is sorted and is lower-case
    public void test_INFRASTRUCTURE_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("INFRASTRUCTURE_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    public void test_COUNTRY_CODE_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("COUNTRY_CODE_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    public void test_GENERIC_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("GENERIC_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    public void test_LOCAL_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("LOCAL_TLDS");
        assertTrue(sorted);
    }

    public void testEnumIsPublic() {
        assertTrue(Modifier.isPublic(DomainValidator.ArrayType.class.getModifiers()));
    }

    public void testGetArray() {
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.INFRASTRUCTURE_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_RO));
    }

    // Download and process local copy of http://data.iana.org/TLD/tlds-alpha-by-domain.txt
    // Check if the internal TLD table is up to date
    // Check if the internal TLD tables have any spurious entries
    public static void main(final String a[]) throws Exception {
        // Check the arrays first as this affects later checks
        // Doing this here makes it easier when updating the lists
        boolean OK = true;
        for(final String list : new String[]{"INFRASTRUCTURE_TLDS","COUNTRY_CODE_TLDS","GENERIC_TLDS","LOCAL_TLDS"}) {
            OK &= isSortedLowerCase(list);
        }
        if (!OK) {
            System.out.println("Fix arrays before retrying; cannot continue");
            return;
        }
        final Set<String> ianaTlds = new HashSet<>(); // keep for comparison with array contents
        final DomainValidator dv = DomainValidator.getInstance();
        final File txtFile = new File("target/tlds-alpha-by-domain.txt");
        final long timestamp = download(txtFile, "https://data.iana.org/TLD/tlds-alpha-by-domain.txt", 0L);
        final File htmlFile = new File("target/tlds-alpha-by-domain.html");
        // N.B. sometimes the html file may be updated a day or so after the txt file
        // if the txt file contains entries not found in the html file, try again in a day or two
        download(htmlFile,"https://www.iana.org/domains/root/db", timestamp);

        final BufferedReader br = new BufferedReader(new FileReader(txtFile));
        String line;
        final String header;
        line = br.readLine(); // header
        if (!line.startsWith("# Version ")) {
            br.close();
            throw new IOException("File does not have expected Version header");
        }
        header = line.substring(2);
        final boolean generateUnicodeTlds = false; // Change this to generate Unicode TLDs as well

        // Parse html page to get entries
        final Map<String, String[]> htmlInfo = getHtmlInfo(htmlFile);
        final Map<String, String> missingTLD = new TreeMap<>(); // stores entry and comments as String[]
        final Map<String, String> missingCC = new TreeMap<>();
        while((line = br.readLine()) != null) {
            if (!line.startsWith("#")) {
                final String unicodeTld; // only different from asciiTld if that was punycode
                final String asciiTld = line.toLowerCase(Locale.ENGLISH);
                if (line.startsWith("XN--")) {
                    unicodeTld = IDN.toUnicode(line);
                } else {
                    unicodeTld = asciiTld;
                }
                if (!dv.isValidTld(asciiTld)) {
                    final String [] info = htmlInfo.get(asciiTld);
                    if (info != null) {
                        final String type = info[0];
                        final String comment = info[1];
                        if ("country-code".equals(type)) { // Which list to use?
                            missingCC.put(asciiTld, unicodeTld + " " + comment);
                            if (generateUnicodeTlds) {
                                missingCC.put(unicodeTld, asciiTld + " " + comment);
                            }
                        } else {
                            missingTLD.put(asciiTld, unicodeTld + " " + comment);
                            if (generateUnicodeTlds) {
                                missingTLD.put(unicodeTld, asciiTld + " " + comment);
                            }
                        }
                    } else {
                        System.err.println("Expected to find HTML info for "+ asciiTld);
                    }
                }
                ianaTlds.add(asciiTld);
                // Don't merge these conditions; generateUnicodeTlds is final so needs to be separate to avoid a warning
                if (generateUnicodeTlds && !unicodeTld.equals(asciiTld)) {
                    ianaTlds.add(unicodeTld);
                }
            }
        }
        br.close();
        // List html entries not in TLD text list
        for(final String key : (new TreeMap<>(htmlInfo)).keySet()) {
            if (!ianaTlds.contains(key)) {
                if (isNotInRootZone(key)) {
                    System.out.println("INFO: HTML entry not yet in root zone: "+key);
                } else {
                    System.err.println("WARN: Expected to find text entry for html: "+key);
                }
            }
        }
        if (!missingTLD.isEmpty()) {
            printMap(header, missingTLD, "TLD");
        }
        if (!missingCC.isEmpty()) {
            printMap(header, missingCC, "CC");
        }
        // Check if internal tables contain any additional entries
        isInIanaList("INFRASTRUCTURE_TLDS", ianaTlds);
        isInIanaList("COUNTRY_CODE_TLDS", ianaTlds);
        isInIanaList("GENERIC_TLDS", ianaTlds);
        // Don't check local TLDS isInIanaList("LOCAL_TLDS", ianaTlds);
        System.out.println("Finished checks");
    }

    private static void printMap(final String header, final Map<String, String> map, final String string) {
        System.out.println("Entries missing from "+ string +" List\n");
        if (header != null) {
            System.out.println("        // Taken from " + header);
        }
        for (Entry<String, String> me : map.entrySet()) {
            System.out.println("        \"" + me.getKey() + "\", // " + me.getValue());
        }
        System.out.println("\nDone");
    }

    private static Map<String, String[]> getHtmlInfo(final File f) throws IOException {
        final Map<String, String[]> info = new HashMap<>();

//        <td><span class="domain tld"><a href="/domains/root/db/ax.html">.ax</a></span></td>
        final Pattern domain = Pattern.compile(".*<a href=\"/domains/root/db/([^.]+)\\.html");
//        <td>country-code</td>
        final Pattern type = Pattern.compile("\\s+<td>([^<]+)</td>");
//        <!-- <td>Åland Islands<br/><span class="tld-table-so">Ålands landskapsregering</span></td> </td> -->
//        <td>Ålands landskapsregering</td>
        final Pattern comment = Pattern.compile("\\s+<td>([^<]+)</td>");

        final BufferedReader br = new BufferedReader(new FileReader(f));
        String line;
        while((line=br.readLine())!=null){
            final Matcher m = domain.matcher(line);
            if (m.lookingAt()) {
                final String dom = m.group(1);
                String typ = "??";
                String com = "??";
                line = br.readLine();
                while (line.matches("^\\s*$")) { // extra blank lines introduced
                    line = br.readLine();
                }
                final Matcher t = type.matcher(line);
                if (t.lookingAt()) {
                    typ = t.group(1);
                    line = br.readLine();
                    if (line.matches("\\s+<!--.*")) {
                        while(!line.matches(".*-->.*")){
                            line = br.readLine();
                        }
                        line = br.readLine();
                    }
                    // Should have comment; is it wrapped?
                    while(!line.matches(".*</td>.*")){
                        line += " " +br.readLine();
                    }
                    final Matcher n = comment.matcher(line);
                    if (n.lookingAt()) {
                        com = n.group(1);
                    }
                    // Don't save unused entries
                    if (com.contains("Not assigned") || com.contains("Retired") || typ.equals("test")) {
//                        System.out.println("Ignored: " + typ + " " + dom + " " +com);
                    } else {
                        info.put(dom.toLowerCase(Locale.ENGLISH), new String[]{typ, com});
//                        System.out.println("Storing: " + typ + " " + dom + " " +com);
                    }
                } else {
                    System.err.println("Unexpected type: " + line);
                }
            }
        }
        br.close();
        return info;
    }

    /*
     * Download a file if it is more recent than our cached copy.
     * Unfortunately the server does not seem to honor If-Modified-Since for the
     * Html page, so we check if it is newer than the txt file and skip download if so
     */
    private static long download(final File f, final String tldurl, final long timestamp) throws IOException {
        final int HOUR = 60*60*1000; // an hour in ms
        final long modTime;
        // For testing purposes, don't download files more than once an hour
        if (f.canRead()) {
            modTime = f.lastModified();
            if (modTime > System.currentTimeMillis()-HOUR) {
                System.out.println("Skipping download - found recent " + f);
                return modTime;
            }
        } else {
            modTime = 0;
        }
        final HttpURLConnection hc = (HttpURLConnection) new URL(tldurl).openConnection();
        if (modTime > 0) {
            final SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");//Sun, 06 Nov 1994 08:49:37 GMT
            final String since = sdf.format(new Date(modTime));
            hc.addRequestProperty("If-Modified-Since", since);
            System.out.println("Found " + f + " with date " + since);
        }
        if (hc.getResponseCode() == 304) {
            System.out.println("Already have most recent " + tldurl);
        } else {
            System.out.println("Downloading " + tldurl);
            final byte buff[] = new byte[1024];
            final InputStream is = hc.getInputStream();

            final FileOutputStream fos = new FileOutputStream(f);
            int len;
            while((len=is.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
            fos.close();
            is.close();
            System.out.println("Done");
        }
        return f.lastModified();
    }

    /**
     * Check whether the domain is in the root zone currently.
     * Reads the URL http://www.iana.org/domains/root/db/*domain*.html
     * (using a local disk cache)
     * and checks for the string "This domain is not present in the root zone at this time."
     * @param domain the domain to check
     * @return true if the string is found
     */
    private static boolean isNotInRootZone(final String domain) {
        final String tldurl = "http://www.iana.org/domains/root/db/" + domain + ".html";
        final File rootCheck = new File("target","tld_" + domain + ".html");
        BufferedReader in = null;
        try {
            download(rootCheck, tldurl, 0L);
            in = new BufferedReader(new FileReader(rootCheck));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("This domain is not present in the root zone at this time.")) {
                    return true;
                }
            }
            in.close();
        } catch (final IOException e) {
        } finally {
            closeQuietly(in);
        }
        return false;
    }

    private static void closeQuietly(final Closeable in) {
        if (in != null) {
            try {
                in.close();
            } catch (final IOException e) {
            }
        }
    }

    // isInIanaList and isSorted are split into two methods.
    // If/when access to the arrays is possible without reflection, the intermediate
    // methods can be dropped
    private static boolean isInIanaList(final String arrayName, final Set<String> ianaTlds) throws Exception {
        final Field f = DomainValidator.class.getDeclaredField(arrayName);
        final boolean isPrivate = Modifier.isPrivate(f.getModifiers());
        if (isPrivate) {
            f.setAccessible(true);
        }
        final String[] array = (String[]) f.get(null);
        try {
            return isInIanaList(arrayName, array, ianaTlds);
        } finally {
            if (isPrivate) {
                f.setAccessible(false);
            }
        }
    }

    private static boolean isInIanaList(final String name, final String [] array, final Set<String> ianaTlds) {
        for (final String element : array) {
            if (!ianaTlds.contains(element)) {
                System.out.println(name + " contains unexpected value: " + element);
            }
        }
        return true;
    }

    private static boolean isSortedLowerCase(final String arrayName) throws Exception {
        final Field f = DomainValidator.class.getDeclaredField(arrayName);
        final boolean isPrivate = Modifier.isPrivate(f.getModifiers());
        if (isPrivate) {
            f.setAccessible(true);
        }
        final String[] array = (String[]) f.get(null);
        try {
            return isSortedLowerCase(arrayName, array);
        } finally {
            if (isPrivate) {
                f.setAccessible(false);
            }
        }
    }

    private static boolean isLowerCase(final String string) {
        return string.equals(string.toLowerCase(Locale.ENGLISH));
    }

    // Check if an array is strictly sorted - and lowerCase
    private static boolean isSortedLowerCase(final String name, final String [] array) {
        boolean sorted = true;
        boolean strictlySorted = true;
        final int length = array.length;
        boolean lowerCase = isLowerCase(array[length-1]); // Check the last entry
        for(int i = 0; i < length-1; i++) { // compare all but last entry with next
            final String entry = array[i];
            final String nextEntry = array[i+1];
            final int cmp = entry.compareTo(nextEntry);
            if (cmp > 0) { // out of order
                System.out.println("Out of order entry: " + entry + " < " + nextEntry + " in " + name);
                sorted = false;
            } else if (cmp == 0) {
                strictlySorted = false;
                System.out.println("Duplicated entry: " + entry + " in " + name);
            }
            if (!isLowerCase(entry)) {
                System.out.println("Non lowerCase entry: " + entry + " in " + name);
                lowerCase = false;
            }
        }
        return sorted && strictlySorted && lowerCase;
    }
}

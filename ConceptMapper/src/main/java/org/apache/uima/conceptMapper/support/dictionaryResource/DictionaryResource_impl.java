/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.uima.conceptMapper.support.dictionaryResource;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;

import org.apache.uima.conceptMapper.support.dictionaryResource.annotatorAdaptor.AnnotatorAdaptor;
import org.apache.uima.conceptMapper.support.tokens.TokenNormalizer;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/*
 * @version $Revision: 1.5 $
 * 
 * @author Michael Tanenblatt <mtan@us.ibm.com> @author (c) Copyright 2005 IBM @author All Rights
 * Reserved
 */
/**
 * Implementation of a UIMA DictionaryResource
 * 
 * 
 * as for the data source binding, the following is allowed:
 * 
 * "classpath:/dictionaries/cm_dishes.xml" --> loads from classpath
 * "file:///tmp/xyz/dictionaries/cm_dishes.xml"
 * 
 * when loading from file, make sure that the path is absolute!
 * 
 */

// public class DictionaryResource_impl extends Resource_ImplBase implements
// DictionaryResource {
public class DictionaryResource_impl implements DictionaryResource, SharedResourceObject {

    private final Logger LOG = LoggerFactory.getLogger(DictionaryResource_impl.class);

    /** Dictionary file loader. Uses an XML parser. */
    protected DictLoader dictLoader;

    /**
     * Hashtable of first words. Contains a DictEntries object keyed on word
     * string for the first word of every entry in the specified dictionary.
     */
    protected Hashtable<String, DictEntriesByLength> dictImpl;

    protected EntryPropertiesRoot entryPropertiesRoot;

    /** Initial size of <code>dict</code> */
    protected static final int NumOfInitialDictEntries = 500000;

    boolean loaded;

    /** Patterns to for matcher to replace SGML &amp;lt; entities */
    private static final Pattern ltPattern = Pattern.compile("&lt;");

    /** Patterns to for matcher to replace SGML &amp;gt; entities */
    private static final Pattern gtPattern = Pattern.compile("&gt;");

    /** Patterns to for matcher to replace SGML &amp;apos; entities */
    private static final Pattern aposPattern = Pattern.compile("&apos;");

    /** Patterns to for matcher to replace SGML &amp;quot; entities */
    private static final Pattern quotPattern = Pattern.compile("&quot;");

    /** Patterns to for matcher to replace SGML &amp;amp; entities */
    private static final Pattern ampPattern = Pattern.compile("&amp;");

    /**
     * Configuration parameter key/label for the order independent lookup
     * indicator
     */

    private boolean sortElements;// = false;

    private boolean dumpDict = false;

    public int entryNum = 0;

    /**
     * 
     */
    public DictionaryResource_impl() {
        super();
        dictImpl = new Hashtable<String, DictEntriesByLength>();
        loaded = false;
    }

    /**
     * @param initialDictEntries
     *            Number of initial dictionary entries
     * 
     */
    public DictionaryResource_impl(int initialDictEntries) {
        super();
        dictImpl = new Hashtable<String, DictEntriesByLength>(initialDictEntries);
        loaded = false;
    }

    /**
     * @return Returns the dictLoader.
     */
    public DictLoader getDictLoader() {
        return dictLoader;
    }

    public DictEntriesByLength getEntries(String key) {
        return dictImpl.get(key);
    }

    /**
     * Create a new dictionary entry.
     * 
     * @param key
     *            the key to index on
     * @param elements
     *            the individual elements to be entered in the dictionary
     * @param unsorted
     *            an unsorted string representation of the entry, if the
     *            contents of 'elements' has been sorted
     * @param length
     *            the number of words in the phrase (>=1)
     * @param props
     *            the EntryProperties object for the dictionary entry
     */
    public void putEntry(String key, String[] elements, String unsorted, int length, EntryProperties props) {
        DictEntriesByLength entry = getEntries(key);

        if (entry == null) {
            entry = new DictEntriesByLength_impl();
            dictImpl.put(key, entry);
        }
        entry.putEntry(length, elements, unsorted, props);
    }

    public void load(DataResource data) throws ResourceInitializationException {
        try {
            dictLoader = new DictLoader(this, data);

            /*
             * At least for now, while info from the AnnotatorContext is not
             * available here, must delay loading until able to access this info
             * 
             * //open input stream to data dictStream = data.getInputStream ();
             * 
             * dictLoader = new DictLoader (data.getLogger (), this);
             * dictLoader.setDictionary (dictStream, NumOfInitialDictEntries);
             */
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }
    }

    public void loadDictionaryContents(TokenNormalizer tokenNormalizer, String tokenAnnotationName,
            String tokenTextFeatureName, String tokenizerDescriptor, String attributeNames[],
            boolean orderIndependentLookup, String dictLanguage, boolean dumpDictionary)
            throws ResourceInitializationException {

        InputStream dictStream = null;
        try {
            sortElements = orderIndependentLookup;
            LOG.info("order independent lookup: " + sortElements);

            dumpDict = dumpDictionary;

            // open input stream to data
            dictStream = dictLoader.getInputStream();
            LOG.info("dict processing language: " + dictLanguage);
            String[] entryPropertyNames = attributeNames;
            LOG.info("dictionary attributes: " + entryPropertyNames);

            entryPropertiesRoot = new EntryPropertiesRoot(entryPropertyNames);
            LOG.info("Loading Dictionary...");

            dictLoader.setDictionary(dictStream, NumOfInitialDictEntries, tokenAnnotationName, tokenTextFeatureName,
                    tokenizerDescriptor, tokenNormalizer, dictLanguage, entryPropertiesRoot);
            LOG.info("...done");

            setLoaded(true);
            if (dumpDict) {
                System.err.println(toString());
            }

        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        } finally {
            if (dictStream != null) {
                try {
                    dictStream.close();
                } catch (IOException e) {
                }
            }
        }

    }

    /**
     * @return Returns the loaded.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * @param loaded
     *            The loaded to set.
     */
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    private static class DictEntriesByLength_impl implements DictEntriesByLength {
        private static final long serialVersionUID = -8150386021246495622L;

        private static class ReverseOrderIntegerComparator implements Comparator<Integer>, Serializable {
            private static final long serialVersionUID = -805437355806223406L;

            public int compare(Integer left, Integer right) {
                // reverse the order of parameters, to reverse the sorting order
                return (right.compareTo(left));
            }

        }

        TreeMap<Integer, DictEntries> entries;

        public DictEntriesByLength_impl() {
            super();
            entries = new TreeMap<Integer, DictEntries>(new ReverseOrderIntegerComparator());
        }

        public DictEntries getEntries(int length) {
            return entries.get(Integer.valueOf(length));
        }

        public void putEntry(int length, String[] elements, String unsorted, EntryProperties props) {
            DictEntries entry = getEntries(length);
            if (entry == null) {
                entry = new DictEntriesImpl();
                entries.put(Integer.valueOf(length), entry);
            }
            entry.putEntry(elements, unsorted, props);
        }

        public Integer getLongest() {
            return entries.firstKey();
        }

        public Integer getShortest() {
            return entries.lastKey();
        }

        public String toString() {
            StringBuilder result = new StringBuilder();

            int i = getLongest().intValue();
            int last = getShortest().intValue();

            while (i >= last) {
                DictEntriesImpl entries = (DictEntriesImpl) getEntries(i);
                if (entries != null) {
                    result.append("<DictEntriesByLength length='" + i + "'>\n");
                    result.append(entries.toString());
                    result.append("</DictEntriesByLength>\n");
                }
                i--;
            }
            return result.toString();
        }
    }

    /**
     * Private class for storing first words in the dict hashtable.
     */
    public static class DictEntriesImpl extends ArrayList<DictEntry> implements DictEntries {
        private static final long serialVersionUID = 1L;

        public DictEntriesImpl() {
        }

        /**
         * Add a new phrase to an existing dictionary entry.
         * 
         * @param elements
         *            the text to be entered in the dictionary
         * @param props
         *            the properties object for the phrase
         */
        public void putEntry(String[] elements, String unsorted, EntryProperties props) {
            add(new DictEntryImpl(elements, unsorted, props));
        }

        public ArrayList<DictEntry> getEntries() {
            return this;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("<DictEntries>");
            for (int i = 0; i < size(); i++) {
                result.append(((DictEntryImpl) get(i)).toString());
            }
            result.append("</DictEntries>");
            return result.toString();
        }

    }

    public static class DictEntryImpl implements DictEntry {
        private static final long serialVersionUID = -7723934333674544157L;

        String[] elements;

        String unsorted;

        EntryProperties properties;

        public DictEntryImpl(String[] elements, String unsorted, EntryProperties properties) {
            super();
            this.properties = properties;
            this.unsorted = unsorted;
            this.elements = elements;
        }

        /**
         * @param properties
         *            The properties to set.
         */
        public void setProperties(EntryProperties properties) {
            this.properties = properties;
        }

        public EntryProperties getProperties() {
            return properties;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("<DictEntry Text ='[");

            boolean firstTime = true;
            for (String element : getElements()) {
                if (firstTime) {
                    firstTime = false;
                } else {
                    result.append(", ");
                }
                result.append(element);
            }
            result.append("''>");
            result.append("</DictEntry>\n");
            return result.toString();

        }

        public String getUnsorted() {
            return unsorted;
        }

        public void setUnsorted(String unsorted) {
            this.unsorted = unsorted;
        }

        public String[] getElements() {
            return elements;
        }

        public void setElements(String[] elements) {
            this.elements = elements;
        }

    }

    /**
     * Convert character entities in a string to the corresponding character.
     * The set of entities handled includes:
     * <ul>
     * <li>&amp;amp;
     * <li>&amp;lt;
     * <li>&amp;gt;
     * <li>&amp;apos;
     * <li>&amp;quot;
     * </ul>
     * 
     * @param input
     *            the string to process.
     * 
     * @return the string with converted entities
     */
    protected String convertEntities(String input) {
        String result = ltPattern.matcher(input).replaceAll("<");
        result = gtPattern.matcher(result).replaceAll(">");
        result = aposPattern.matcher(result).replaceAll("'");
        result = quotPattern.matcher(result).replaceAll("\"");
        result = ampPattern.matcher(result).replaceAll("&");

        return (result);
    }

    /**
     * Private class to load the dictionary file. Extends the
     * org.xml.sax.helpers.DefaultHandler for XML parsing.
     */
    private class DictLoader extends DefaultHandler {
        /** Default parser name. */

        /** Default name of element that contains dictionary records. */
        protected static final String DEFAULT_TOKEN_ELEM = "token";

        /** Default name of element that contains variant form */
        protected static final String DEFAULT_VARIANT_ELEM = "variant";

        /**
         * Default name of attribute in the token element that contains the key
         * for the entry (i.e., the surface or variant form that will be found
         * in the document).
         */
        protected static final String DEFAULT_KEY_ATTR = "base";

        /** Default name of element that contains part of speech */
        // protected static final String DEFAULT_POS_ATTR = "POS";
        /** The XML parser that parses the dictionary file. */
        private XMLReader parser = null;

        /** The name of the element that contains the dictionary records. */
        private String token_elem = DEFAULT_TOKEN_ELEM;

        /**
         * The name of the element that contains a variant form for the current
         * entry
         */
        private String variant_elem = DEFAULT_VARIANT_ELEM;

        /** The name of the attribute that contains the key. */
        private String key_attribute = DEFAULT_KEY_ATTR;

        /** The name of the attribute that contains the part of speech. */
        // private String pos_attribute = DEFAULT_POS_ATTR;
        /** Count of number of dictionary entries loaded. */
        private int term_cnt = 0;

        /** The hashtable built while parsing the dictionary file. */
        private DictionaryResource dict;

        /** Properties for current canonical form */
        private EntryProperties props;

        private AnnotatorAdaptor adaptor;
        private String tokenizerDescriptor;
        private TokenNormalizer tokenNormalizer;
        private EntryPropertiesRoot entryPropertiesRoot;

        /**
         * needed to access input stream, since cannot load external dict
         * resource until TAE config params are available to set up tokenizer
         * correctly
         */
        DataResource dataResource;

        private Vector<DictionaryToken> result;

        /**
         * Create a dictionary loader.
         * 
         * @throws Exception
         *             if XML parser cannot be created.
         */
        public DictLoader(DictionaryResource dict, DataResource data) throws Exception {
            this.dict = dict;
            this.dataResource = data;

            // create parser
            try {
                parser = XMLReaderFactory.createXMLReader();
            } catch (Exception e) {
                LOG.error("Unable to instantiate dictionary parser: " + e.getMessage());
                throw (e);
            }
            parser.setContentHandler(this);
            parser.setErrorHandler(this);

        }

        protected String getTokenizerDescriptor() {
            return tokenizerDescriptor;
        }

        protected void setTokenizerDescriptor(String tokenizerDescriptor) {
            this.tokenizerDescriptor = tokenizerDescriptor;
        }

        protected void setTokenNormalizer(TokenNormalizer tokenNormalizer) {
            this.tokenNormalizer = tokenNormalizer;
        }

        protected void setEntryPropertiesRoot(EntryPropertiesRoot entryPropertiesRoot) {
            this.entryPropertiesRoot = entryPropertiesRoot;
        }

        protected EntryPropertiesRoot getPropertiesRoot() {
            return entryPropertiesRoot;
        }

        /**
         * Start element. This method does most of the work of building the
         * hashtable.
         * 
         */
        public void startElement(String uri, String local, String raw, Attributes attrs) throws SAXException {

            DictionaryToken token = null;

            if (raw.equals(token_elem)) { // starting new token entry
                if (attrs != null) {

                    props = getPropertiesRoot().newEntryProperties();
                    int attrCount = attrs.getLength();
                    for (int i = 0; i < attrCount; i++) {
                        props.setProperty(attrs.getQName(i), convertEntities(attrs.getValue(i)));
                    }

                }
            } else if (raw.equals(variant_elem)) { // variant for current token
                if (attrs != null) {
                    int attrCount = attrs.getLength();
                    ArrayList<String> tokens = new ArrayList<String>();

                    // if this variant contains its own POS info, save token
                    // level POS info and set props to
                    // contain variant's
                    EntryProperties variantProperties = new EntryProperties(props);
                    for (int i = 0; i < attrCount; i++) {
                        if (attrs.getQName(i).equals(key_attribute)) { // key
                                                                       // attribute?

                            adaptor.runCPM(convertEntities(attrs.getValue(i)));

                            Iterator<DictionaryToken> tokenIter = result.iterator();
                            token = null;

                            while (tokenIter.hasNext()) {
                                token = (DictionaryToken) tokenIter.next();
                                break;
                            }

                            if (token == null) {
                                return;
                            }
                            tokens.add(tokenNormalizer.normalize(token.getText()));
                            while (tokenIter.hasNext()) {
                                token = (DictionaryToken) tokenIter.next();
                                String tokenText = tokenNormalizer.normalize(token.getText());

                                tokens.add(tokenText);
                            }
                        } else {
                            variantProperties.setProperty(attrs.getQName(i), convertEntities(attrs.getValue(i)));
                        }

                    }

                    String[] elements = (String[]) tokens.toArray(new String[tokens.size()]);

                    String unsorted = null;

                    if (sortElements) {
                        unsorted = stringTogetherTokens(elements);
                        Arrays.sort(elements);
                    }

                    // add to dictionary
                    if (sortElements) {
                        for (int i = 0; i < tokens.size(); i++) {
                            dict.putEntry((String) tokens.get(i), elements, unsorted, elements.length,
                                    variantProperties);
                        }
                    } else {
                        dict.putEntry((String) tokens.get(0), elements, unsorted, elements.length, variantProperties);
                    }
                    term_cnt++;
                    if ((term_cnt % 10000) == 0) {
                        LOG.info("processed " + term_cnt + " entries");
                    }
                }
            }
        }

        //
        // ErrorHandler methods
        //
        /** Warning. */
        public void warning(SAXParseException ex) throws SAXException {
            LOG.warn(errorString("Warning", ex));
        } // warning(SAXParseException)

        /**
         * Error. public void error (SAXParseException ex) throws SAXException {
         * getLogger ().log (Level.SEVERE, errorString ("Error", ex)); } //
         * error(SAXParseException)
         */
        /** Fatal error. */
        public void fatalError(SAXParseException ex) throws SAXException {
            LOG.info(errorString("Fatal Error", ex));
            throw ex;
        } // fatalError(SAXParseException)

        /** Prints the error message. */
        protected String errorString(String type, SAXParseException ex) {
            String errorMsg = "[" + type + "]";
            if (ex == null) {
                return errorMsg + "!!!";
            }
            String systemId = ex.getSystemId();
            if (systemId != null) {
                int index = systemId.lastIndexOf('/');
                if (index != -1)
                    systemId = systemId.substring(index + 1);
                errorMsg += systemId;
            }
            errorMsg += ":" + ex.getLineNumber() + ":" + ex.getColumnNumber() + ": " + ex.getMessage();
            return errorMsg;
        }

        /**
         * Use the tokenizer specified in 'tokenizerDescriptor' to load the
         * specified dicitonary file. The dictonary file must be in the format
         * specified above. A new <code>dict</code> hashtable is created with a
         * <code>DictEntries</code> object for each unique first word in the
         * base fields in the dictionary file. The <code>dict</code> hashtable
         * is keyed off of the first word. The <code>DictEntries</code> for a
         * first word contains a hashtable of <code>DictEntry</code> objects for
         * every phrase in the base fields of the dictionary file started by the
         * first word. The phrase hashtable is keyed off of the entire phrase.
         * 
         * @param tokenizerDescriptor
         * @param tokenAnnotationName
         * @param tokenFilter
         * 
         * @param dictFile
         *            the fully specified filename of the dictionary file to
         *            load.
         * 
         * @param NumOfInitialDictEntries
         *            initial size of hashtable to create
         * 
         * @exception java.io.IOException
         *                if dictionary file cannot be loaded or some other
         *                initialization error occurs.
         */
        public void setDictionary(InputStream dictStream, int initialDictEntries, String tokenAnnotationName,
                String tokenTextFeatureName, String tokenizerDescriptor, TokenNormalizer tokenNormalizer, String langID,
                EntryPropertiesRoot entryPropertiesRoot) throws DictionaryLoaderException {

            LOG.info("loading dictionary with these settings:");
            LOG.info("token type: " + tokenAnnotationName);
            LOG.info("token feature: " + tokenTextFeatureName);
            LOG.info("preprocessing: " + tokenizerDescriptor);
            LOG.info("normalizer: " + tokenNormalizer);

            term_cnt = 0;
            setTokenizerDescriptor(tokenizerDescriptor);
            setTokenNormalizer(tokenNormalizer);
            result = new Vector<DictionaryToken>();

            setEntryPropertiesRoot(entryPropertiesRoot);

            LOG.info("Loading dictionary");
            try {
                adaptor = new AnnotatorAdaptor(getTokenizerDescriptor(), result, tokenAnnotationName,
                        tokenTextFeatureName, langID);
                adaptor.initCPM();

                parser.parse(new InputSource(dictStream));
            } catch (SAXException e) {
                LOG.error("Parse error occurred - " + e.getMessage());
                throw new DictionaryLoaderException(e);
            } catch (IOException e) {
                throw new DictionaryLoaderException(e);
            }
            LOG.info("Finished loading " + term_cnt + " entries");
        }

        public InputStream getInputStream() throws IOException {

            // try loading from classpath first
            URI dataResourceUri = dataResource.getUri();

            if (dataResourceUri.getScheme().equalsIgnoreCase("classpath")) {
                InputStream in = this.getClass().getResourceAsStream(dataResourceUri.getPath());
                LOG.info("loading data resource from classpath: " + dataResourceUri.getPath());
                return in;
            } else {
                LOG.info("loading data resource via file: " + dataResourceUri);
                return dataResource.getInputStream();
            }
        }

    }

    public DictionaryResource newDictionaryResource(int initialDictEntries) {
        return new DictionaryResource_impl(initialDictEntries);
    }

    public Enumeration<String> keys() {
        return dictImpl.keys();
    }

    public static String stringTogetherTokens(String[] elements) {
        StringBuilder tokenString = new StringBuilder();

        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                tokenString.append(" ");
            }
            tokenString.append(elements[i]);
        }
        return tokenString.toString();
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        Enumeration<String> e = keys();

        while (e.hasMoreElements()) {
            String key = e.nextElement();
            result.append("<DictionaryItem key='" + key + "'>\n");
            DictEntriesByLength_impl item = (DictEntriesByLength_impl) getEntries(key);
            result.append(item.toString());
            result.append("</DictionaryItem>\n");
        }
        return result.toString();
    }

    public void serializeEntries(FileOutputStream output) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(output);
        oos.writeObject(this.entryPropertiesRoot);
        oos.writeObject(this.dictImpl);
        oos.close();
    }

    public EntryPropertiesRoot getEntryPropertiesRoot() {
        return entryPropertiesRoot;
    }

}
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
package org.apache.uima.conceptMapper.support.dictionaryResource.annotatorAdaptor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.conceptMapper.support.dictionaryResource.DictionaryLoaderException;
import org.apache.uima.conceptMapper.support.dictionaryResource.DictionaryToken;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotatorAdaptor {

    private final Logger LOG = LoggerFactory.getLogger(AnnotatorAdaptor.class);

    private ResourceSpecifier aeSpecifier;

    private AnalysisEngine ae;

    private CAS cas;

    private Vector<DictionaryToken> result;

    private String tokenTypeName;
    private String tokenTextFeatureName;

    private Type tokenType;
    private Feature tokenTextFeature;
    private boolean typeSystemInitialized = false;

    private String langID;

    public AnnotatorAdaptor(String analysisEngineDescriptorPath, Vector<DictionaryToken> result, String tokenTypeName,
            String tokenTextFeatureName, String langID) throws DictionaryLoaderException {
        super();
        try {
            aeSpecifier = UIMAFramework.getXMLParser()
                    .parseResourceSpecifier(new XMLInputSource(analysisEngineDescriptorPath));
            this.tokenTypeName = tokenTypeName;
            this.tokenTextFeatureName = tokenTextFeatureName;
            LOG.info("token type:  " + tokenTypeName + ": " + tokenTypeName + ", feat: " + tokenTextFeatureName);

            this.langID = langID;
            this.result = result;
        } catch (InvalidXMLException e) {
            throw new DictionaryLoaderException(e);
        } catch (IOException e) {
            throw new DictionaryLoaderException(e);
        }
    }

    public void initCPM() throws DictionaryLoaderException {
        try {
            ResourceManager resMgr = UIMAFramework.newDefaultResourceManager();
            String dp = System.getProperty("uima.datapath");
            if (null != dp) {
                resMgr.setDataPath(dp);
            }
            ae = UIMAFramework.produceAnalysisEngine(aeSpecifier);
            cas = ae.newCAS();
        } catch (ResourceInitializationException e) {
            throw new DictionaryLoaderException(e);
        } catch (MalformedURLException e) {
            throw new DictionaryLoaderException(e);
        }
    }

    public void runCPM(String text) {
        cas.setDocumentText(text);
        cas.setDocumentLanguage(langID);

        try {
            ae.process(cas);
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
        }
        processCAS(cas);
        cas.reset();
    }

    private void processCAS(CAS cas) {
        if (cas == null) {
        } else {

            // System.err.println ("processCAS(), doc text: " +
            // cas.getDocumentText());
            result.clear();
            if (!typeSystemInitialized) {
                TypeSystem typeSystem = cas.getTypeSystem();
                tokenType = typeSystem.getType(tokenTypeName);
                if (tokenTextFeatureName != null) {
                    tokenTextFeature = tokenType.getFeatureByBaseName(tokenTextFeatureName);
                } else {
                    tokenTextFeature = null;
                }
                typeSystemInitialized = true;
            }

            FSIterator tokenIter = cas.getIndexRepository().getAllIndexedFS(tokenType);
            // System.err.println ("any tokens found? " + tokenIter.hasNext ());
            while (tokenIter.hasNext()) {
                AnnotationFS annotation = (AnnotationFS) tokenIter.next();
                DictionaryToken dt = new DictionaryToken(annotation, tokenTextFeature);
                // System.out.println("new dict entry: " + dt.getText());
                result.add(dt);
            }
            if (result.size() == 0) {
                // System.err.println ("Dictionary tokenization of: '" +
                // cas.getDocumentText() + "' produced no tokens of type: '" +
                // tokenType.getName () + "'");
                LOG.info("Dictionary tokenization of: '" + cas.getDocumentText() + "' produced no tokens of type: '"
                        + tokenType.getName() + "'");
            }
        }
    }
}

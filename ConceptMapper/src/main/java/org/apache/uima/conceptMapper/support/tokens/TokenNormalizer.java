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

package org.apache.uima.conceptMapper.support.tokens;

import java.util.regex.Pattern;

import org.apache.uima.analysis_engine.annotator.AnnotatorContextException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenNormalizer {

    private final Logger LOG = LoggerFactory.getLogger(TokenNormalizer.class);

    private static Pattern CapPat = Pattern.compile("^[A-Z][a-z]+$");

    private static Pattern HasDigit = Pattern.compile("[0-9]");

    private boolean caseFoldAll;

    private boolean caseFoldInitCap;

    private boolean caseFoldDigit;

    private String CASE_INSENSITIVE = "insensitive";

    private String CASE_FOLD_DIGITS = "digitfold";

    private String CASE_IGNORE = "ignoreall";

    //TODO need serious cleanup
    public TokenNormalizer(String caseMatch) throws AnnotatorContextException {
        super();


        this.setCaseFoldInitCap(false);
        this.setCaseFoldDigit(false);
        this.setCaseFoldAll(false);

        if (caseMatch != null) {
            if (caseMatch.equalsIgnoreCase(CASE_INSENSITIVE)) {
                this.setCaseFoldInitCap(true);
                LOG.info("case match set to: setCaseFoldInitCap");
            } else if (caseMatch.equalsIgnoreCase(CASE_FOLD_DIGITS)) {
                this.setCaseFoldDigit(true);
                LOG.info("case match set to: setCaseFoldDigit");
            } else if (caseMatch.equalsIgnoreCase(CASE_IGNORE)) {
                this.setCaseFoldAll(true);
                LOG.info("case match set to: setCaseFoldAll");
            } else {
                LOG.error("unrecognized case match type: " + caseMatch);
            }
        }

    }

    public boolean isCaseFoldAll() {
        return caseFoldAll;
    }

    public void setCaseFoldAll(boolean caseFoldAll) {
        this.caseFoldAll = caseFoldAll;
    }

    public boolean isCaseFoldDigit() {
        return caseFoldDigit;
    }

    public void setCaseFoldDigit(boolean caseFoldDigit) {
        this.caseFoldDigit = caseFoldDigit;
    }

    public boolean isCaseFoldInitCap() {
        return caseFoldInitCap;
    }

    public void setCaseFoldInitCap(boolean caseFoldInitCap) {
        this.caseFoldInitCap = caseFoldInitCap;
    }

    public boolean shouldFoldCase(String token) {
        return (caseFoldAll || (caseFoldInitCap && CapPat.matcher(token).matches())
                || (caseFoldDigit && HasDigit.matcher(token).find()));
    }

    /**
     * If one of the case folding flags is true and the input string matches the
     * character pattern corresponding to that flag, then convert all letters to
     * lowercase.
     * 
     * @param token
     *            The string to case fold
     * 
     * @return The case folded string
     */
    public String foldCase(String token) {
        if (shouldFoldCase(token)) {
            return token.trim().toLowerCase();
        }
        return token;
    }

    public String normalize(String token) {
        return foldCase(token);
    }
}

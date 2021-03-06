/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.decorators;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.*;

import org.geoserver.security.SecurityUtils;
import org.geoserver.security.WrapperPolicy;
import org.geoserver.security.impl.SecureObjectsTest;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;

public class ReadOnlyDataStoreTest extends SecureObjectsTest {

    private DataStore ds;

    @Before
    public void setUp() throws Exception {
        SimpleFeatureStore fs = createNiceMock(SimpleFeatureStore.class);
        expect(fs.getSchema()).andReturn(DataUtilities.createType("test", "g:Polygon,name:String")).anyTimes();
        replay(fs);
        ds = createNiceMock(DataStore.class);
        expect(ds.getFeatureSource("blah")).andReturn(fs);
        replay(ds);
    }

    @Test
    public void testDisallowedAPI() throws Exception {
        ReadOnlyDataStore ro = new ReadOnlyDataStore(ds, WrapperPolicy.hide(null));

        try {
            ro.createSchema(null);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }
        try {
            ro.updateSchema((String) null, null);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }

        try {
            ro.updateSchema((Name) null, null);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }
        try {
            ro.getFeatureWriter("states", Transaction.AUTO_COMMIT);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }
        try {
            ro.getFeatureWriter("states", Filter.INCLUDE,
                    Transaction.AUTO_COMMIT);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }
        try {
            ro.getFeatureWriterAppend("states", Transaction.AUTO_COMMIT);
            fail("Should have failed with an unsupported operation exception");
        } catch (UnsupportedOperationException e) {
            //
        }
    }
    
    @Test
    public void testChallenge() throws Exception {
        ReadOnlyDataStore ro = new ReadOnlyDataStore(ds, WrapperPolicy.readOnlyChallenge(null));

        try {
            ro.createSchema(null);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }
        try {
            ro.updateSchema((String) null, null);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }

        try {
            ro.updateSchema((Name) null, null);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }
        try {
            ro.getFeatureWriter("states", Transaction.AUTO_COMMIT);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }
        try {
            ro.getFeatureWriter("states", Filter.INCLUDE,
                    Transaction.AUTO_COMMIT);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }
        try {
            ro.getFeatureWriterAppend("states", Transaction.AUTO_COMMIT);
            fail("Should have failed with a security exception");
        } catch (Exception e) {
            if (ReadOnlyDataStoreTest.isSpringSecurityException(e)==false)
                fail("Should have failed with a security exception");
        }
    }
    
    static public boolean isSpringSecurityException (Exception ex) {
        return SecurityUtils.isSecurityException(ex);
    }
}

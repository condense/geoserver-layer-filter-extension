/*
 * Copyright 2014 IMOS
 *
 * The AODN/IMOS Portal is distributed under the terms of the GNU General Public License
 *
 */

package au.org.emii.geoserver.extensions.filters;

import au.org.emii.geoserver.extensions.filters.layer.data.Filter;
import au.org.emii.geoserver.extensions.filters.layer.data.FilterConfiguration;
import au.org.emii.geoserver.extensions.filters.layer.data.FilterMerge;
import au.org.emii.geoserver.extensions.filters.layer.data.io.FilterConfigurationIO;
import au.org.emii.geoserver.extensions.filters.layer.data.io.FilterConfigurationReader;
import au.org.emii.geoserver.extensions.filters.layer.data.io.LayerPropertiesReader;
import au.org.emii.geoserver.extensions.filters.layer.data.io.LayerPropertiesReaderFactory;
import org.apache.commons.io.IOUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.CSSPackageResource;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.data.store.DataAccessEditPage;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jndi.JndiTemplate;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LayerFilterConfigurationPage extends GeoServerSecuredPage {

    static Logger LOGGER = Logging.getLogger("au.org.emii.geoserver.extensions.filters");

    public static final String NAME = "name";

    private String layerName;
    private String storeName;
    private String workspaceName;
    private String dataDirectory;

    @Autowired
    private ServletContext context;

    public LayerFilterConfigurationPage(PageParameters parameters) {
        this(
            parameters.getString(DataAccessEditPage.WS_NAME),
            parameters.getString(DataAccessEditPage.STORE_NAME),
            parameters.getString(NAME)
        );
    }

    public LayerFilterConfigurationPage(String workspaceName, String storeName, String layerName) {
        this.workspaceName = workspaceName;
        this.storeName = storeName;
        this.layerName = layerName;

        try {
            add(getLayerFilterForm());
            add(CSSPackageResource.getHeaderContribution(LayerFilterConfigurationPage.class, "layer_filters.css"));
        }
        catch (NamingException e) {
            LOGGER.log(Level.SEVERE, "Error getting DataSource from JNDI reference", e);
        }
        catch (ParserConfigurationException pce) {
            LOGGER.log(Level.SEVERE, "Could not parse saved filter configuration", pce);
        }
        catch (SAXException se) {
            LOGGER.log(Level.SEVERE, "Could not parse saved filter configuration", se);
        }
        catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Error reading filters", ioe);
        }
    }

    @Override
    protected String getTitle() {
        return String.format("%s/%s/%s", workspaceName, storeName, layerName);
    }

    @Override
    protected String getDescription() {
        return String.format("Configuring filters for %s/%s/%s", workspaceName, storeName, layerName);
    }

    public void setContext(ServletContext context) {
        this.context = context;
    }

    private LayerFilterForm getLayerFilterForm() throws NamingException, ParserConfigurationException, SAXException, IOException {
        return new LayerFilterForm("layerFilterForm", getFilterConfigurationModel());
    }

    private DataSource getDataSource() throws NamingException {
        JndiTemplate template = new JndiTemplate();
        return (DataSource)template.lookup(getDataStoreParameter("jndiReferenceName"));
    }

    private DataStoreInfo getDataStoreInfo() {
        return getCatalog().getDataStoreByName(workspaceName, storeName);
    }

    private String getDataStoreParameter(String parameter) {
        return (String)getDataStoreInfo().getConnectionParameters().get(parameter);
    }

    private List<Filter> getLayerProperties() throws NamingException, IOException {
        LayerPropertiesReader layerPropertiesReader = LayerPropertiesReaderFactory.getReader(getDataSource(), layerName, getDataStoreParameter("schema"));
        return layerPropertiesReader.read();
    }

    private List<Filter> getConfiguredFilters() throws ParserConfigurationException, SAXException, IOException {
        List<Filter> filters = new ArrayList<Filter>();
        File file = new File(String.format("%s/%s", getDataDirectory(), FilterConfigurationIO.FILTER_CONFIGURATION_FILE_NAME));
        if (file.exists()) {
            filters = readFilterConfigurationFromFile(file);
        }

        return filters;
    }

    private List<Filter> readFilterConfigurationFromFile(File file) throws ParserConfigurationException, SAXException, IOException {
        FilterConfigurationReader filterConfigurationReader = new FilterConfigurationReader(getDataDirectory());
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            return filterConfigurationReader.read(fileInputStream).getFilters();
        }
        finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }

    private IModel<FilterConfiguration> getFilterConfigurationModel() throws NamingException, ParserConfigurationException, SAXException, IOException {

        final FilterConfiguration config = new FilterConfiguration(getDataDirectory(), FilterMerge.merge(getLayerProperties(), getConfiguredFilters()));

        return new Model<FilterConfiguration>() {
            @Override
            public FilterConfiguration getObject() {
                return config;
            }
        };
    }

    private String getDataDirectory() {
        if (dataDirectory == null) {
            dataDirectory = Paths.path(getGeoServerDataDirectory(), "workspaces", workspaceName, storeName, layerName);
        }

        return dataDirectory;
    }

    private String getGeoServerDataDirectory() {
        return GeoServerResourceLoader.lookupGeoServerDataDirectory(context);
    }
}

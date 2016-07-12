package lsoc.gateway.standalone.store.sqlite;

import lsoc.gateway.standalone.data.Resource;
import lsoc.gateway.standalone.store.Store;
import org.slf4j.Logger;
import org.sql2o.Connection;
import org.sql2o.Sql2o;
import org.sql2o.Sql2oException;
import org.sql2o.converters.Convert;
import org.sql2o.converters.Converter;
import org.sql2o.converters.ConverterException;
import org.sql2o.quirks.NoQuirks;

import javax.xml.bind.DatatypeConverter;
import java.util.*;

@SuppressWarnings("PackageAccessibility")
public class SqliteStoreImpl implements Store {
    private static Logger logger = org.slf4j.LoggerFactory.getLogger(SqliteStoreImpl.class);
    private Sql2o database;

    public void start() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Convert.registerConverter(Calendar.class, new CalendarConverter());
        Convert.registerConverter(GregorianCalendar.class, new CalendarConverter());
        database = new Sql2o("jdbc:sqlite:/tmp/lol.db", null, null);
        database.open().createQuery("DROP TABLE IF EXISTS resource").executeUpdate();
        database.open().createQuery("CREATE TABLE IF NOT EXISTS resource (" +
                "id TEXT PRIMARY KEY, " +
                "owner TEXT NOT NULL, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "type TEXT NOT NULL, " +
                "value TEXT NOT NULL, " +
                "actions TEXT NOT NULL)").executeUpdate();
    }

    @Override
    public Collection<Resource> getResources() {
        List<Resource> resources;
        resources = database.open().createQuery("SELECT * FROM resource")
                .executeAndFetch(Resource.class);
        return resources;
    }

    @Override
    public Resource getResource(String resourceId) {
        return database.open().createQuery("SELECT * FROM resource WHERE id = :id LIMIT 1")
                .addParameter("id", resourceId)
                .executeAndFetchFirst(Resource.class);
    }

    @Override
    public boolean putResource(Resource resource) {
        try {
            database.open().createQuery("INSERT OR REPLACE INTO resource(id, owner, timestamp, type, value, actions) " +
                    "VALUES(:id, :owner, :timestamp, :type, :value, :actions)")
                    .bind(resource)
                    .executeUpdate();
            return true;
        } catch (Sql2oException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deleteResource(String resourceId) {
        Connection connection = database.open().createQuery("DELETE FROM resource WHERE id = :id")
                .addParameter("id", resourceId)
                .executeUpdate();
        return connection.getResult() > 0;
    }

    @Override
    public void deleteResources() {
        database.open().createQuery("DELETE FROM resource").executeUpdate();
    }

    private static class CalendarConverter implements Converter<Calendar> {
        @Override
        public Calendar convert(Object o) throws ConverterException {
            try {
                return DatatypeConverter.parseDateTime((String) o);
            } catch (IllegalArgumentException e) {
                throw new ConverterException("Malformed ISO8601 date string", e);
            }
        }

        @Override
        public Object toDatabaseParam(Calendar calendar) {
            return DatatypeConverter.printDateTime(calendar);
        }
    }
}

package com.billme.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@SpringBootTest
public class SchemaDiagnoseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void inspectSchema() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            System.out.println("DATABASE PRODUCT: " + metaData.getDatabaseProductName());
            
            try (ResultSet rs = metaData.getColumns(null, null, "products", null)) {
                System.out.println("COLUMNS IN 'products' TABLE:");
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int columnSize = rs.getInt("COLUMN_SIZE");
                    System.out.println(" - " + columnName + " (" + typeName + ", size: " + columnSize + ")");
                }
            }
        }
    }
}

package com.alibaba.druid.bvt.pool;

import java.lang.management.ManagementFactory;
import java.sql.Connection;

import javax.management.ObjectName;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.alibaba.druid.mock.MockDriver;
import com.alibaba.druid.pool.DruidDataSource;

public class TestIdle3 extends TestCase {

    public void test_idle2() throws Exception {
        MockDriver driver = new MockDriver();

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:mock:xxx");
        dataSource.setDriver(driver);
        dataSource.setInitialSize(1);
        dataSource.setMaxActive(14);
        dataSource.setMaxIdle(14);
        dataSource.setMinIdle(1);
        dataSource.setMinEvictableIdleTimeMillis(30 * 100); // 300 / 10
        dataSource.setTimeBetweenEvictionRunsMillis(18 * 100); // 180 / 10
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setFilters("stat");
        
        ManagementFactory.getPlatformMBeanServer().registerMBean(dataSource, new ObjectName("com.alibaba:type=DataSource,name=mysql"));
        ManagementFactory.getPlatformMBeanServer().registerMBean(dataSource, new ObjectName("com.alibaba:type=DataSource,name=oracle"));

        // 第一次创建连接
        {
            Assert.assertEquals(0, dataSource.getCreateCount());
            Assert.assertEquals(0, dataSource.getActiveCount());

            Connection conn = dataSource.getConnection();

            Assert.assertEquals(dataSource.getInitialSize(), dataSource.getCreateCount());
            Assert.assertEquals(dataSource.getInitialSize(), driver.getConnections().size());
            Assert.assertEquals(1, dataSource.getActiveCount());

            conn.close();
            Assert.assertEquals(0, dataSource.getDestroyCount());
            Assert.assertEquals(2, driver.getConnections().size());
            Assert.assertEquals(2, dataSource.getCreateCount());
            Assert.assertEquals(0, dataSource.getActiveCount());
        }

        {
            // 并发创建14个
            int count = 14;
            Connection[] connections = new Connection[count];
            for (int i = 0; i < count; ++i) {
                connections[i] = dataSource.getConnection();
                Assert.assertEquals(i + 1, dataSource.getActiveCount());
            }

            Assert.assertEquals(dataSource.getMaxActive(), dataSource.getCreateCount());
            Assert.assertEquals(count, driver.getConnections().size());

            // 全部关闭
            for (int i = 0; i < count; ++i) {
                connections[i].close();
                Assert.assertEquals(count - i - 1, dataSource.getActiveCount());
            }

            Assert.assertEquals(dataSource.getMaxActive(), dataSource.getCreateCount());
            Assert.assertEquals(0, dataSource.getActiveCount());
            Assert.assertEquals(14, driver.getConnections().size());
        }

        // 连续打开关闭单个连接
        for (int i = 0; i < 1000; ++i) {
            Assert.assertEquals(0, dataSource.getActiveCount());
            Connection conn = dataSource.getConnection();

            Assert.assertEquals(1, dataSource.getActiveCount());

            Thread.sleep(10);
            conn.close();
        }
        Assert.assertEquals(true, dataSource.getPoolingCount() == 2 || dataSource.getPoolingCount() == 1);

        dataSource.close();
    }
}
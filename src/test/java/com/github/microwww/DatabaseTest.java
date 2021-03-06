package com.github.microwww;

import org.junit.Assert;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.IOException;
import java.util.List;

public class DatabaseTest extends AbstractRedisTest {

    @Test
    public void testConnection() {
        Jedis jd = jedis;
        String result = jd.ping();
        Assert.assertEquals(result, "PONG");
        result = jd.select(1);
        Assert.assertEquals(result, Protocol.Keyword.OK.name());
        String val = "daatata";
        jd.set("test", val);
        result = jd.get("test");
        Assert.assertEquals(result, val);
        jd.select(2);
        result = jd.get("test");
        Assert.assertNull(result);
        try {
            jd.echo("ee");
        } catch (JedisDataException e) {
            result = jd.ping();
            Assert.assertEquals(result, "PONG");
        }
        List<String> time = jd.time();
        Assert.assertEquals(2, time.size());
        Assert.assertEquals(10, time.get(0).length());
        jd.close();
    }

    // @Test
    public void test() {
        Jedis jd = new Jedis("192.168.2.18");
        jd.auth("123456");
        String result = jd.ping();
        String ss = jd.get("---");
    }
}
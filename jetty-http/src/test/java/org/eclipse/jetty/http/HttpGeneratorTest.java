// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package org.eclipse.jetty.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.text.View;

import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.SimpleBuffers;
import org.eclipse.jetty.util.BufferUtil;
import org.junit.Test;

/**
 *
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class HttpGeneratorTest
{
    public final static String CONTENT="The quick brown fox jumped over the lazy dog.\nNow is the time for all good men to come to the aid of the party\nThe moon is blue to a fish in love.\n";
    public final static String[] connect={null,"keep-alive","close","TE, close"};

    @Test
    public void testRequest() throws Exception
    {
        ByteBuffer header=BufferUtil.allocate(8096);
        HttpFields fields = new HttpFields();
        HttpGenerator gen = new HttpGenerator();

        fields.add("Host","something");
        fields.add("User-Agent","test");

        gen.setRequest(HttpMethod.GET,"/index.html",HttpVersion.HTTP_1_1);
        
        HttpGenerator.Result 
        result=gen.complete(null,null);
        assertEquals(HttpGenerator.Result.NEED_COMMIT,result);
        
        result=gen.commit(fields,header,null,null,true);
        String out = BufferUtil.toString(header);
        BufferUtil.clear(header);
        assertEquals(HttpGenerator.Result.NEED_COMPLETE,result);
        result=gen.complete(null,null);
        assertEquals(HttpGenerator.Result.OK,result);
        
        assertTrue(out.indexOf("GET /index.html HTTP/1.1")==0);
        assertTrue(out.indexOf("Content-Length")==-1);
        
        assertEquals(HttpGenerator.State.END,gen.getState());
        assertEquals(0,gen.getContentWritten());    }
    
    @Test
    public void testRequestWithSmallContent() throws Exception
    {
        ByteBuffer header=BufferUtil.allocate(8096);
        ByteBuffer buffer=BufferUtil.allocate(8096);
        ByteBuffer content=BufferUtil.toBuffer("Hello World");
        HttpFields fields = new HttpFields();
        HttpGenerator gen = new HttpGenerator();

        gen.setVersion(HttpVersion.HTTP_1_1);
        gen.setRequest("POST","/index.html");
        fields.add("Host","something");
        fields.add("User-Agent","test");

        HttpGenerator.Result 
        
        result=gen.prepareContent(null,null,content);
        assertEquals(HttpGenerator.Result.NEED_BUFFER,result);
        
        result=gen.prepareContent(null,buffer,content);
        assertEquals(HttpGenerator.Result.OK,result);
        assertEquals("Hello World",BufferUtil.toString(buffer));
        assertTrue(BufferUtil.isEmpty(content));

        result=gen.complete(null,buffer);
        assertEquals(HttpGenerator.Result.NEED_COMMIT,result);
        result=gen.commit(fields,header,buffer,content,true);
        assertEquals(HttpGenerator.Result.FLUSH,result);
        String out = BufferUtil.toString(header);
        BufferUtil.clear(header);
        BufferUtil.clear(buffer);
        
        result=gen.complete(null,buffer);
        assertEquals(HttpGenerator.Result.OK,result);
        
        
        result=gen.commit(fields,header,null,null,true);
        assertEquals(HttpGenerator.Result.NEED_COMPLETE,result);
        result=gen.complete(null,null);
        assertEquals(HttpGenerator.Result.OK,result);
        

        assertTrue(out.indexOf("GET /index.html HTTP/1.1")==0);
        assertTrue(out.indexOf("Content-Length")==-1);
        
        assertEquals(HttpGenerator.State.END,gen.getState());
        assertEquals(0,gen.getContentWritten());    
    }

    
    
    
    @Test
    public void testHTTP() throws Exception
    {
        ByteBuffer bb=new ByteArrayBuffer(8096);
        ByteBuffer sb=new ByteArrayBuffer(1500);
        HttpFields fields = new HttpFields();
        ByteArrayEndPoint endp = new ByteArrayEndPoint(new byte[0],4096);
        HttpGenerator hb = new HttpGenerator(new SimpleBuffers(sb,bb),endp);
        Handler handler = new Handler();
        HttpParser parser=null;

        
        
        // For HTTP version
        for (int v=9;v<=11;v++)
        {
            // For each test result
            for (int r=0;r<tr.length;r++)
            {
                // chunks = 1 to 3
                for (int chunks=1;chunks<=6;chunks++)
                {
                    // For none, keep-alive, close
                    for (int c=0;c<(v==11?connect.length:(connect.length-1));c++)
                    {
                        String t="v="+v+",r="+r+",chunks="+chunks+",connect="+c+",tr="+tr[r];
                        // System.err.println(t);

                        hb.reset();
                        endp.reset();
                        fields.clear();

                        tr[r].build(v,hb,"OK\r\nTest",connect[c],null,chunks, fields);
                        String response=endp.getOut().toString();
                        //System.out.println("RESPONSE: "+t+"\n"+response+(hb.isPersistent()?"...\n":"---\n"));

                        if (v==9)
                        {
                            assertFalse(t,hb.isPersistent());
                            if (tr[r]._body!=null)
                                assertEquals(t,tr[r]._body, response);
                            continue;
                        }

                        parser=new HttpParser(new ByteArrayBuffer(response.getBytes()), handler);
                        parser.setHeadResponse(tr[r]._head);
                                
                        try
                        {
                            parser.parse();
                        }
                        catch(IOException e)
                        {
                            if (tr[r]._body!=null)
                                throw new Exception(t,e);
                            continue;
                        }

                        if (tr[r]._body!=null)
                            assertEquals(t,tr[r]._body, this.content);
                        
                        if (v==10)
                            assertTrue(t,hb.isPersistent() || tr[r]._contentLength==null || c==2 || c==0);
                        else
                            assertTrue(t,hb.isPersistent() ||  c==2 || c==3);

                        if (v>9)
                            assertEquals("OK  Test",f2);

                        if (content==null)
                            assertTrue(t,tr[r]._body==null);
                        else
                            assertTrue(t,tr[r]._contentLength==null || content.length()==Integer.parseInt(tr[r]._contentLength));
                    }
                }
            }
        }
    }

    private static final String[] headers= { "Content-Type","Content-Length","Connection","Transfer-Encoding","Other"};
    private static class TR
    {
        private int _code;
        private String _body;
        private boolean _head;
        String _contentType;
        String _contentLength;
        String _connection;
        String _te;
        String _other;

        private TR(int code,String contentType, String contentLength ,String content,boolean head)
        {
            _code=code;
            _contentType=contentType;
            _contentLength=contentLength;
            _other="value";
            _body=content;
            _head=head;
        }

        private void build(int version,HttpGenerator hb,String reason, String connection, String te, int chunks, HttpFields fields) throws Exception
        {
            _connection=connection;
            _te=te;
            hb.setVersion(version);
            hb.setResponse(_code,reason);
            hb.setHead(_head);
           
            if (_contentType!=null)
                fields.put(new ByteArrayBuffer("Content-Type"),new ByteArrayBuffer(_contentType));
            if (_contentLength!=null)
                fields.put(new ByteArrayBuffer("Content-Length"),new ByteArrayBuffer(_contentLength));
            if (_connection!=null)
                fields.put(new ByteArrayBuffer("Connection"),new ByteArrayBuffer(_connection));
            if (_te!=null)
                fields.put(new ByteArrayBuffer("Transfer-Encoding"),new ByteArrayBuffer(_te));
            if (_other!=null)
                fields.put(new ByteArrayBuffer("Other"),new ByteArrayBuffer(_other));
            
            if (_body!=null)
            {
                int inc=1+_body.length()/chunks;
                ByteBuffer buf=new ByteArrayBuffer(_body);
                View view = new View(buf);
                for (int i=1;i<chunks;i++)
                {
                    view.setPutIndex(i*inc);
                    view.setGetIndex((i-1)*inc);
                    hb.addContent(view,Generator.MORE);
                    if (hb.isBufferFull() && hb.isState(AbstractGenerator.STATE_HEADER))
                        hb.completeHeader(fields, Generator.MORE);
                    if (i%2==0)
                    {
                        if (hb.isState(AbstractGenerator.STATE_HEADER))
                            hb.completeHeader(fields, Generator.MORE);
                        hb.flushBuffer();
                    }
                }
                view.setPutIndex(buf.putIndex());
                view.setGetIndex((chunks-1)*inc);
                hb.addContent(view,Generator.LAST);
                if(hb.isState(AbstractGenerator.STATE_HEADER))
                    hb.completeHeader(fields, Generator.LAST);
            }
            else
            {
                hb.completeHeader(fields, Generator.LAST);
            }
            hb.complete();
            
            while(!hb.isComplete())
                hb.flushBuffer();
        }

        @Override
        public String toString()
        {
            return "["+_code+","+_contentType+","+_contentLength+","+(_body==null?"null":"content")+"]";
        }
    }

    private final TR[] tr =
    {
      /* 0 */  new TR(200,null,null,null,false),
      /* 1 */  new TR(200,null,null,CONTENT,false),
      /* 2 */  new TR(200,null,""+CONTENT.length(),null,true),
      /* 3 */  new TR(200,null,""+CONTENT.length(),CONTENT,false),
      /* 4 */  new TR(200,"text/html",null,null,true),
      /* 5 */  new TR(200,"text/html",null,CONTENT,false),
      /* 6 */  new TR(200,"text/html",""+CONTENT.length(),null,true),
      /* 7 */  new TR(200,"text/html",""+CONTENT.length(),CONTENT,false),
    };

    private String content;
    private String f0;
    private String f1;
    private String f2;
    private String[] hdr;
    private String[] val;
    private int h;

    private class Handler extends HttpParser.EventHandler
    {
        private int index=0;

        @Override
        public void content(ByteBuffer ref)
        {
            if (index == 0)
                content= "";
            content= content.substring(0, index) + ref;
            index+=ref.length();
        }

        @Override
        public void startRequest(ByteBuffer tok0, ByteBuffer tok1, ByteBuffer tok2)
        {
            h= -1;
            hdr= new String[9];
            val= new String[9];
            f0= tok0.toString();
            f1= tok1.toString();
            if (tok2!=null)
                f2= tok2.toString();
            else
                f2=null;
            index=0;
            // System.out.println(f0+" "+f1+" "+f2);
        }

        /* (non-Javadoc)
         * @see org.eclipse.jetty.EventHandler#startResponse(org.eclipse.io.Buffer, int, org.eclipse.io.Buffer)
         */
        @Override
        public void startResponse(ByteBuffer version, int status, ByteBuffer reason)
        {
            h= -1;
            hdr= new String[9];
            val= new String[9];
            f0= version.toString();
            f1= ""+status;
            if (reason!=null)
                f2= reason.toString();
            else
                f2=null;
            index=0;
        }

        @Override
        public void parsedHeader(ByteBuffer name,ByteBuffer value)
        {
            hdr[++h]= name.toString();
            val[h]= value.toString();
        }

        @Override
        public void headerComplete()
        {
            content= null;
        }

        @Override
        public void messageComplete(long contentLength)
        {
        }
    }
}

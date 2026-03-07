package dev.automata.automata.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "vs")
public class WledXmlResponse {

    @JacksonXmlProperty(localName = "ac")
    public int ac; // brightness

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "cl")
    public List<Integer> cl; // primary RGB

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "cs")
    public List<Integer> cs; // secondary RGB

    @JacksonXmlProperty(localName = "ns")
    public int ns;

    @JacksonXmlProperty(localName = "nr")
    public int nr;

    @JacksonXmlProperty(localName = "nl")
    public int nl;

    @JacksonXmlProperty(localName = "nf")
    public int nf;

    @JacksonXmlProperty(localName = "nd")
    public int nd;

    @JacksonXmlProperty(localName = "nt")
    public int nt;

    @JacksonXmlProperty(localName = "fx")
    public int fx;

    @JacksonXmlProperty(localName = "sx")
    public int sx;

    @JacksonXmlProperty(localName = "ix")
    public int ix;

    @JacksonXmlProperty(localName = "fp")
    public int fp;

    @JacksonXmlProperty(localName = "wv")
    public int wv;

    @JacksonXmlProperty(localName = "ws")
    public int ws;

    @JacksonXmlProperty(localName = "ps")
    public int ps;

    @JacksonXmlProperty(localName = "cy")
    public int cy;

    @JacksonXmlProperty(localName = "ds")
    public String ds;

    @JacksonXmlProperty(localName = "ss")
    public int ss;
}

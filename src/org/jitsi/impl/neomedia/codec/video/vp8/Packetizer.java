/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.vp8;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;

/**
 * Packetizes VP8 encoded frames in accord with
 * {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
 *
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that
 * changes is the Start of Partition bit, which is set only for the first packet
 * encoding a frame.
 *
 * @author Boris Grozev
 */
public class Packetizer
    extends AbstractCodecExt
{
    /**
     * The <tt>Logger</tt> used by the <tt>Packetizer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(Packetizer.class);

    /**
     * Maximum size of packets (excluding the payload descriptor and any other
     * headers (RTP, UDP))
     */
    static final int MAX_SIZE = 1350;

    /**
     * Whether this is the first packet from the frame.
     */
    private boolean firstPacket = true;

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super("VP8 Packetizer",
                VideoFormat.class,
                new VideoFormat[] { new VideoFormat(Constants.VP8_RTP)});
        inputFormats = new VideoFormat[] { new VideoFormat(Constants.VP8)};
    }

    /**
     * {@inheritDoc}
     */
    protected void doClose()
    {
        return;
    }

    /**
     * {@inheritDoc}
     */
    protected void doOpen()
    {
        if(logger.isTraceEnabled())
            logger.trace("Opened VP8 packetizer");
        return;
    }

    /**
     * {@inheritDoc}
     * @param inputBuffer input <tt>Buffer</tt>
     * @param outputBuffer output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> or <tt>INPUT_BUFFER_NOT_CONSUMED</tt>
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        if(inputBuffer.isDiscard() || ((byte[])inputBuffer.getData()).length == 0)
        {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        byte[] output;
        int offset;
        final int pdMaxLen = DePacketizer.VP8PayloadDescriptor.MAX_LENGTH;

        //The input will fit in a single packet
        if(inputBuffer.getLength() <= MAX_SIZE)
        {
            output = validateByteArraySize(outputBuffer,
                                           inputBuffer.getLength() + pdMaxLen);
            offset = pdMaxLen;
        }
        else
        {
            output = validateByteArraySize(outputBuffer, MAX_SIZE + pdMaxLen);
            offset = pdMaxLen;
        }

        int len = inputBuffer.getLength() <= MAX_SIZE
                    ? inputBuffer.getLength()
                    : MAX_SIZE;

        System.arraycopy((byte[])inputBuffer.getData(),
                         inputBuffer.getOffset(),
                         output,
                         offset,
                         len);

        //get the payload descriptor and copy it to the output
        byte[] pd = DePacketizer.VP8PayloadDescriptor.create(firstPacket);
        System.arraycopy(pd,
                            0,
                            output,
                            offset - pd.length,
                            pd.length);
        offset -= pd.length;

        //set up the output buffer
        outputBuffer.setFormat(new VideoFormat(Constants.VP8_RTP));
        outputBuffer.setOffset(offset);
        outputBuffer.setLength(len + pd.length);

        if(inputBuffer.getLength() <= MAX_SIZE)
        {
            firstPacket = true;
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            firstPacket = false;
            inputBuffer.setLength(inputBuffer.getLength()- MAX_SIZE);
            inputBuffer.setOffset(inputBuffer.getOffset()+ MAX_SIZE);
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }
}
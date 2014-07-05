/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;

/**
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class RTCPFeedbackMessageSender
{
    private final RTPTranslatorImpl rtpTranslator;

    private Map<Long,Integer> sequenceNumbers
        = new LinkedHashMap<Long,Integer>();

    public RTCPFeedbackMessageSender(RTPTranslatorImpl rtpTranslator)
    {
        this.rtpTranslator = rtpTranslator;
    }

    private int getNextSequenceNumber(int sourceSSRC, int targetSSRC)
    {
        synchronized (sequenceNumbers)
        {
            Long key
                = Long.valueOf(
                        ((sourceSSRC & 0xffffffffl) << 32)
                            | (targetSSRC & 0xffffffffl));
            Integer value = sequenceNumbers.get(key);
            int seqNr = (value == null) ? 0 : value.intValue();

            sequenceNumbers.put(key, Integer.valueOf(seqNr + 1));
            return seqNr;
        }
    }

    private long getSenderSSRC()
    {
        long ssrc = rtpTranslator.getLocalSSRC(null);

        return (ssrc == Long.MAX_VALUE) ? -1 : (ssrc & 0xffffffffl);
    }

    public boolean sendFIR(MediaStream destination, int mediaSenderSSRC)
    {
        long senderSSRC = getSenderSSRC();

        if (senderSSRC == -1)
            return false;

        RTCPFeedbackMessagePacket fir
            = new RTCPFeedbackMessagePacket(
                    RTCPFeedbackMessageEvent.FMT_FIR,
                    RTCPFeedbackMessageEvent.PT_PS,
                    senderSSRC,
                    0xffffffffl & mediaSenderSSRC);

        fir.setSequenceNumber(
                getNextSequenceNumber((int) senderSSRC, mediaSenderSSRC));
        return rtpTranslator.writeRTCPFeedbackMessage(fir, destination);
    }

    public boolean sendFIR(MediaStream destination, int[] mediaSenderSSRCs)
    {
        boolean sentFIR = false;

        for (int mediaSenderSSRC : mediaSenderSSRCs)
        {
            if (sendFIR(destination, mediaSenderSSRC))
                sentFIR = true;
        }
        return sentFIR;
    }

    public boolean sendFIR(int mediaSenderSSRC)
    {
        boolean sentFIR = false;
        /*
         * XXX Currently this methond results in a FIR message being effectively
         * broadcast (sent to all streams connected to the translator). This
         * is because the MediaStreams' getRemoteSourceIds returns an empty list
         * (possibly due to RED being used).
         */
        for (StreamRTPManager streamRTPManager
                : rtpTranslator.getStreamRTPManagers())
        {
            MediaStream stream = streamRTPManager.getMediaStream();
            sentFIR |= sendFIR(stream, mediaSenderSSRC);
        }

        return sentFIR;
    }
}

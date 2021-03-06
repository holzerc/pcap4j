/*_##########################################################################
  _##
  _##  Copyright (C) 2014  Kaito Yamada
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

import static org.pcap4j.util.ByteArrays.*;
import java.util.ArrayList;
import java.util.List;
import org.pcap4j.packet.factory.PacketFactories;
import org.pcap4j.packet.namednumber.Ssh2MessageNumber;
import org.pcap4j.util.ByteArrays;

/**
 * @author Kaito Yamada
 * @since pcap4j 1.0.1
 */
public final class Ssh2BinaryPacket extends AbstractPacket {

  /*
   * http://tools.ietf.org/html/rfc4253
   *
   * Each packet is in the following format:
   *
   *   uint32    packet_length
   *   byte      padding_length
   *   byte[n1]  payload; n1 = packet_length - padding_length - 1
   *   byte[n2]  random padding; n2 = padding_length
   *   byte[m]   mac (Message Authentication Code - MAC); m = mac_length
   *
   *   packet_length
   *      The length of the packet in bytes, not including 'mac' or the
   *      'packet_length' field itself.
   *
   *   padding_length
   *      Length of 'random padding' (bytes).
   *
   *   payload
   *      The useful contents of the packet.  If compression has been
   *      negotiated, this field is compressed.  Initially, compression
   *      MUST be "none".
   *
   *   random padding
   *      Arbitrary-length padding, such that the total length of
   *      (packet_length || padding_length || payload || random padding)
   *      is a multiple of the cipher block size or 8, whichever is
   *      larger.  There MUST be at least four bytes of padding.  The
   *      padding SHOULD consist of random bytes.  The maximum amount of
   *      padding is 255 bytes.
   *
   *   mac
   *      Message Authentication Code.  If message authentication has
   *      been negotiated, this field contains the MAC bytes.  Initially,
   *      the MAC algorithm MUST be "none".
   *
   * Note that the length of the concatenation of 'packet_length',
   * 'padding_length', 'payload', and 'random padding' MUST be a multiple
   * of the cipher block size or 8, whichever is larger.
   *
   * The minimum size of a packet is 16 (or the cipher block size,
   * whichever is larger) bytes (plus 'mac').
   */

  /**
   *
   */
  private static final long serialVersionUID = 6484755289384336675L;

  private final Ssh2BinaryHeader header;
  private final Packet payload;
  private final byte[] randomPadding;
  private final byte[] mac;

  /**
   *
   * @param rawData
   * @return a new Ssh2BinaryPacket object.
   */
  public static Ssh2BinaryPacket newPacket(byte[] rawData) {
    return new Ssh2BinaryPacket(rawData);
  }

  private Ssh2BinaryPacket(byte[] rawData) {
    if (rawData == null) {
      throw new NullPointerException();
    }
    this.header = new Ssh2BinaryHeader(rawData);

    byte[] rawPayload
      = ByteArrays.getSubArray(
          rawData,
          5,
          header.getPacketLength() - header.getPaddingLength() - 1
        );
    if (rawPayload.length == 0) {
      StringBuilder sb = new StringBuilder(120);
      sb.append("Payload is required for Ssh2BinaryPacket. data: ")
        .append(ByteArrays.toHexString(rawData, " "));
      throw new IllegalRawDataException(sb.toString());
    }

    this.payload
      = PacketFactories.getFactory(Packet.class, Ssh2MessageNumber.class)
          .newInstance(rawPayload, Ssh2MessageNumber.getInstance(rawPayload[0]));

    this.randomPadding
      = ByteArrays.getSubArray(
          rawData,
          5 + payload.length(),
          header.getPaddingLength()
        );

    this.mac
      = ByteArrays.getSubArray(
          rawData,
          5 + payload.length() + randomPadding.length
        );
  }

  private Ssh2BinaryPacket(Builder builder) {
    if (
         builder == null
      || builder.payloadBuilder == null
      || builder.mac == null
    ) {
      StringBuilder sb = new StringBuilder();
      sb.append("builder: ").append(builder)
        .append(" builder.payloadBuilder: ").append(builder.payloadBuilder)
        .append(" builder.randomPadding: ").append(builder.randomPadding)
        .append(" builder.mac: ").append(builder.mac);
      throw new NullPointerException(sb.toString());
    }

    if (!builder.paddingAtBuild && builder.randomPadding == null) {
      throw new NullPointerException(
                  "builder.randomPadding must not be null"
                    + " if builder.paddingAtBuild is false"
                );
    }

    this.payload = builder.payloadBuilder.build();

    if (builder.paddingAtBuild) {
      int blockSize = builder.cipherBlockSize > 8 ? builder.cipherBlockSize : 8;
      int paddingSize = payload.length() % blockSize;
      this.randomPadding = new byte[paddingSize];
    }
    else {
      this.randomPadding = new byte[builder.randomPadding.length];
      System.arraycopy(
        builder.randomPadding, 0, this.randomPadding, 0, builder.randomPadding.length
      );
    }

    this.header = new Ssh2BinaryHeader(builder, payload.length(), (byte)randomPadding.length);
    this.mac = new byte[builder.mac.length];
    System.arraycopy(
      builder.mac, 0, this.mac, 0, builder.mac.length
    );
  }

  @Override
  public Ssh2BinaryHeader getHeader() {
    return header;
  }

  @Override
  public Packet getPayload() {
    return payload;
  }

  /**
   *
   * @return randomPadding
   */
  public byte[] getRandomPadding() {
    byte[] copy = new byte[randomPadding.length];
    System.arraycopy(randomPadding, 0, copy, 0, randomPadding.length);
    return copy;
  }

  /**
   *
   * @return mac
   */
  public byte[] getMac() {
    byte[] copy = new byte[mac.length];
    System.arraycopy(mac, 0, copy, 0, mac.length);
    return copy;
  }

  @Override
  public Builder getBuilder() {
    return new Builder(this);
  }

  /**
   *
   * @author Kaito Yamada
   * @since pcap4j 1.0.1
   */
  public static final class Builder extends AbstractBuilder
  implements LengthBuilder<Ssh2BinaryPacket>{

    private int packetLength;
    private byte paddingLength;
    private Packet.Builder payloadBuilder;
    private byte[] randomPadding;
    private byte[] mac;
    private boolean correctLengthAtBuild;
    private int cipherBlockSize = 0;
    private boolean paddingAtBuild;


    /**
     *
     */
    public Builder() {}

    private Builder(Ssh2BinaryPacket packet) {
      this.packetLength = packet.header.packetLength;
      this.paddingLength = packet.header.paddingLength;
      this.payloadBuilder = packet.payload.getBuilder();
      this.randomPadding = packet.randomPadding;
      this.mac = packet.mac;
    }

    /**
     *
     * @param packetLength
     * @return this Builder object for method chaining.
     */
    public Builder packetLength(int packetLength) {
      this.packetLength = packetLength;
      return this;
    }

    /**
     *
     * @param paddingLength
     * @return this Builder object for method chaining.
     */
    public Builder paddingLength(byte paddingLength) {
      this.paddingLength = paddingLength;
      return this;
    }

    @Override
    public Builder payloadBuilder(Packet.Builder payloadBuilder) {
      this.payloadBuilder = payloadBuilder;
      return this;
    }

    @Override
    public Packet.Builder getPayloadBuilder() {
      return payloadBuilder;
    }

    /**
     *
     * @param randomPadding
     * @return this Builder object for method chaining.
     */
    public Builder randomPadding(byte[] randomPadding) {
      this.randomPadding = randomPadding;
      return this;
    }

    /**
     *
     * @param mac
     * @return this Builder object for method chaining.
     */
    public Builder mac(byte[] mac) {
      this.mac = mac;
      return this;
    }

    public Builder correctLengthAtBuild(boolean correctLengthAtBuild) {
      this.correctLengthAtBuild = correctLengthAtBuild;
      return this;
    }

    /**
     *
     * @param cipherBlockSize
     * @return this Builder object for method chaining.
     */
    public Builder cipherBlockSize(int cipherBlockSize) {
      this.cipherBlockSize = cipherBlockSize;
      return this;
    }

    /**
     *
     * @param paddingAtBuild
     * @return this Builder object for method chaining.
     */
    public Builder paddingAtBuild(boolean paddingAtBuild) {
      this.paddingAtBuild = paddingAtBuild;
      return this;
    }

    @Override
    public Ssh2BinaryPacket build() {
      return new Ssh2BinaryPacket(this);
    }

  }

  /**
   *
   * @author Kaito Yamada
   * @version pcap4j 1.0.1
   */
  public static final class Ssh2BinaryHeader extends AbstractHeader {

    /*
     *  0                            15
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |        packet_length          |
     * +                               +
     * |                               |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |padding_length |
     * +-+-+-+-+-+-+-+-+
     */

    /**
     *
     */
    private static final long serialVersionUID = -7927092563030949527L;

    private static final int PACKET_LENGTH_OFFSET = 0;
    private static final int PACKET_LENGTH_SIZE = INT_SIZE_IN_BYTES;
    private static final int PADDING_LENGTH_OFFSET = PACKET_LENGTH_OFFSET + PACKET_LENGTH_SIZE;
    private static final int PADDING_LENGTH_SIZE = BYTE_SIZE_IN_BYTES;
    private static final int SSH2_BINARY_HEADER_SIZE = PADDING_LENGTH_OFFSET + PADDING_LENGTH_SIZE;

    private final int packetLength;
    private final byte paddingLength;

    private Ssh2BinaryHeader(byte[] rawData) {
      if (rawData.length < SSH2_BINARY_HEADER_SIZE) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("The data is too short to build an SSH2 Binary header(")
          .append(SSH2_BINARY_HEADER_SIZE)
          .append(" bytes). data: ")
          .append(ByteArrays.toHexString(rawData, " "));
        throw new IllegalRawDataException(sb.toString());
      }

      this.packetLength = ByteArrays.getInt(rawData, PACKET_LENGTH_OFFSET);
      this.paddingLength = ByteArrays.getByte(rawData, PADDING_LENGTH_OFFSET);

      if (packetLength < 0) {
        StringBuilder sb = new StringBuilder(120);
        sb.append("The packet length which is longer than 2147483647 is not supported. packet length: ")
          .append(getPacketLengthAsLong());
        throw new IllegalRawDataException(sb.toString());
      }
    }

    private Ssh2BinaryHeader(Builder builder, int payloadLength, byte paddingLength) {
      if (builder.correctLengthAtBuild) {
        this.packetLength = payloadLength;
        this.paddingLength = paddingLength;
      }
      else {
        this.packetLength = builder.packetLength;
        this.paddingLength = builder.paddingLength;
      }

      if (packetLength < 0) {
        StringBuilder sb = new StringBuilder(120);
        sb.append("The packet length which is longer than 2147483647 is not supported. packet length: ")
          .append(builder.packetLength & 0xFFFFFFFFL);
        throw new IllegalArgumentException(sb.toString());
      }
    }

    /**
     *
     * @return packetLength
     */
    public int getPacketLength() {
      return packetLength;
    }

    /**
     *
     * @return packetLength
     */
    public long getPacketLengthAsLong() {
      return 0xFFFFFFFFL & packetLength;
    }

    /**
     *
     * @return paddingLength
     */
    public byte getPaddingLength() {
      return paddingLength;
    }

    /**
     *
     * @return paddingLength
     */
    public int getPaddingLengthAsInt() {
      return 0xFF & paddingLength;
    }

    @Override
    protected List<byte[]> getRawFields() {
      List<byte[]> rawFields = new ArrayList<byte[]>();
      rawFields.add(ByteArrays.toByteArray(packetLength));
      rawFields.add(ByteArrays.toByteArray(paddingLength));
      return rawFields;
    }

    @Override
    public int length() { return SSH2_BINARY_HEADER_SIZE; }

    @Override
    protected String buildString() {
      StringBuilder sb = new StringBuilder();
      String ls = System.getProperty("line.separator");

      sb.append("[SSH2 Binary Packet Header (")
        .append(length())
        .append(" bytes)]")
        .append(ls);
      sb.append("  packet_length: ")
        .append(packetLength)
        .append(ls);
      sb.append("  padding_length: ")
        .append(paddingLength)
        .append(ls);

      return sb.toString();
    }

  }

}

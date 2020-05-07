
package com.hedera.services.legacy.client.util;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * This is a utility file to read back service record file generated by Hedera node
 */
public class RecordFileParser {

	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("SERVICE_RECORD");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed


	/**
	 * Given a service record name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash and list of transaction and record pairs
	 */
	static public Pair<byte[], List<Pair<Transaction, TransactionRecord>>> loadRecordFile(String fileName) {
		File file = new File(fileName);
		FileInputStream stream = null;
		List<Pair<Transaction, TransactionRecord>> txList = new LinkedList<>();
		byte[] prevFileHash = null;

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}

		RecordFileLogger.initFile(fileName);

		try {
			long counter = 0;
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			prevFileHash = new byte[48];
			int record_format_version = dis.readInt();
			int version = dis.readInt();

			log.info(MARKER, "Record file format version " + record_format_version);
			log.info(MARKER, "HAPI protocol version " + version);

			while (dis.available() != 0) {

				try {
					byte typeDelimiter = dis.readByte();

					switch (typeDelimiter) {
						case TYPE_PREV_HASH:
							dis.read(prevFileHash);
							log.info(MARKER, "Previous file Hash = " + Hex.encodeHexString(prevFileHash));
							break;
						case TYPE_RECORD:
							int byteLength = dis.readInt();
							byte[] rawBytes = new byte[byteLength];

							dis.readFully(rawBytes);
							Transaction transaction = Transaction.parseFrom(rawBytes);

							byteLength = dis.readInt();
							rawBytes = new byte[byteLength];
							dis.readFully(rawBytes);
							TransactionRecord txRecord = TransactionRecord.parseFrom(rawBytes);

							txList.add(Pair.of(transaction, txRecord));

							counter++;
							RecordFileLogger.storeRecord(counter, RequestBuilder.convertProtoTimeStamp(txRecord.getConsensusTimestamp()), transaction, txRecord);
							log.info(MARKER, "record counter = {}\n=============================", counter);
							log.info(MARKER, "Transaction Consensus Timestamp = {}\n", RequestBuilder.convertProtoTimeStamp(txRecord.getConsensusTimestamp()));
							log.info(MARKER, "Transaction = {}", RecordFileParser.printTransaction(transaction));
							log.info(MARKER, "Record = {}\n=============================\n",  TextFormat.shortDebugString(txRecord));
							break;
						case TYPE_SIGNATURE:
							int sigLength = dis.readInt();
							log.info(MARKER, "sigLength = " + sigLength);
							byte[] sigBytes = new byte[sigLength];
							dis.readFully(sigBytes);
							log.info(MARKER, "File {} Signature = {} ", fileName, Hex.encodeHexString(sigBytes));
							RecordFileLogger.storeSignature(Hex.encodeHexString(sigBytes));
							break;

						default:
							log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
					}

				} catch (Exception e) {
					log.error(LOGM_EXCEPTION, "Exception ", e);
					break;
				}
			}
			dis.close();
		} catch (FileNotFoundException e) {
			log.error(MARKER, "File Not Found Error");
		} catch (IOException e) {
			log.error(MARKER, "IOException Error");
		} catch (Exception e) {
			log.error(MARKER, "Parsing Error");
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("Exception in close the stream {}", ex);
			}
			RecordFileLogger.completeFile();
		}

		return Pair.of(prevFileHash, txList);
	}

	/**
	 * read and parse a list of record files
	 */
	static public void loadRecordFiles(List<String> fileNames) {

		byte[] calculatedPrevHash = null;
		for (String name : fileNames) {
			Pair<byte[], List<Pair<Transaction, TransactionRecord>>> result = loadRecordFile(name);
			byte[] readPrevHash = result.getKey();
			if (calculatedPrevHash != null) {
				if (!Arrays.equals(calculatedPrevHash, readPrevHash)) {

					log.error(LOGM_EXCEPTION, "calculatedPrevHash " + Hex.encodeHexString(calculatedPrevHash));
					log.error(LOGM_EXCEPTION, "readPrevHash       " + Hex.encodeHexString(readPrevHash));
					log.error(LOGM_EXCEPTION, "Error Exception, hash does not match: " + name);

				}
			}
			byte[] thisFileHash = getFileHash(name);
			calculatedPrevHash = thisFileHash;

			moveFileToParsedDir(name);
			log.info(MARKER, "File Hash = {} \n==========================================================",
					Hex.encodeHexString(thisFileHash));
		}
	}

	static void moveFileToParsedDir(String fileName) {
		File sourceFile = new File(fileName);
		File parsedDir = new File(sourceFile.getParentFile().getParentFile().getPath() + "/parsedRecordFiles/");
		parsedDir.mkdirs();
		File destFile = new File(parsedDir.getPath() + "/" + sourceFile.getName());
		try {
			Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			log.info(MARKER, sourceFile.toPath() + " has been moved to " + destFile.getPath());
		} catch (IOException ex) {
			log.error(MARKER, "Fail to move {} to {} : {}",
					fileName, parsedDir.getName(),
					ex.getStackTrace());
		}
	}

	/**
	 * Calculate SHA384 hash of a binary file
	 *
	 * @param fileName
	 * 		file name
	 * @return byte array of hash value
	 */
	public static byte[] getFileHash(String fileName) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-384");

			byte[] array = new byte[0];
			try {
				array = Files.readAllBytes(Paths.get(fileName));
			} catch (IOException e) {
				log.error("Exception ", e);
			}
			byte[] fileHash = md.digest(array);
			return fileHash;

		} catch (NoSuchAlgorithmException e) {
			log.error(LOGM_EXCEPTION, "Exception ", e);
			return null;
		}
	}

	/**
	 * Given a service record name, read its prevFileHash
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash's Hex String
	 */
	static public String readPrevFileHash(String fileName) {
		File file = new File(fileName);
		FileInputStream stream = null;
		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}
		byte[] prevFileHash = new byte[48];
		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			// record_format_version
			dis.readInt();
			// version
			dis.readInt();

			byte typeDelimiter = dis.readByte();

			if (typeDelimiter == TYPE_PREV_HASH) {
				dis.read(prevFileHash);
				String hexString = Hex.encodeHexString(prevFileHash);
				log.info(MARKER, "readPrevFileHash :: Previous file Hash = {}, file name = {}", hexString, fileName);
				return hexString;
			} else {
				log.error(MARKER, "readPrevFileHash :: Should read Previous file Hash, but found file delimiter {}, file name = {}", typeDelimiter, fileName);
			}
			dis.close();

		} catch (FileNotFoundException e) {
			log.error(MARKER, "readPrevFileHash :: File Not Found Error, file name = {}",  fileName);
		} catch (IOException e) {
			log.error(MARKER, "readPrevFileHash :: IOException Error, file name = {}",  fileName);
		} catch (Exception e) {
			log.error(MARKER, "readPrevFileHash :: Parsing Error, file name = {}",  fileName);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("readPrevFileHash :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}

	/**
	 * print a Transaction's content to a String
	 * @param transaction
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public static String printTransaction(final Transaction transaction) throws InvalidProtocolBufferException {
		StringBuilder stringBuilder = new StringBuilder();
		if (transaction.hasSigs()) {
			stringBuilder.append(TextFormat.shortDebugString(transaction.getSigs()) + "\n");
		}
		if (transaction.hasSigMap()) {
			stringBuilder.append(TextFormat.shortDebugString(transaction.getSigMap()) + "\n");
		}

		stringBuilder.append(TextFormat.shortDebugString(
				getTransactionBody(transaction)) + "\n");
		return stringBuilder.toString();
	}

	public static TransactionBody getTransactionBody(final Transaction transaction)  throws InvalidProtocolBufferException {
		if (transaction.hasBody()) {
			return transaction.getBody();
		} else {
			return TransactionBody.parseFrom(transaction.getBodyBytes());
		}
	}
}

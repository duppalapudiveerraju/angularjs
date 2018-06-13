package org.ril.hrss.sapendpoint.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.ril.hrss.msf.model.UserAuth;
import org.ril.hrss.msf.util.HRSSConstantUtil;
import org.ril.hrss.msf.util.ObjectMapperUtil;
import org.ril.hrss.msf.util.SAPErrorHandlerUtil;
import org.ril.hrss.sapendpoint.intercomm.CacheStoreClient;
import org.ril.hrss.sapendpoint.model.SAPErrorMessage;
import org.ril.hrss.sapendpoint.model.SAPPmeErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RefreshScope
@Component
public class SapEndpointUtil {

	@Value("${sap.endpoint.url.csrf.token:null}")
	private String sapEndpointCSRFTokenURL;

	@Value("${sap.endpoint.url.auth:null}")
	private String sapEndpointAuthURL;

	protected static final Logger logger = Logger.getLogger(SapEndpointUtil.class.getName());

	private Map<String, UserAuth> userMap;

	@Autowired
	private ObjectMapperUtil objectMapperUtil;

	@Autowired
	private SAPErrorHandlerUtil sapErrorHandlerUtil;

	@Autowired
	private CacheStoreClient cacheStoreClient;

	public SapEndpointUtil() {
		super();
		userMap = setUserMap();
	}

	public ResponseEntity<UserAuth> validateAuth(UserAuth userAuthObj) {
		logger.info("SapEndpointUtil.validateAuth()");
		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet request = new HttpGet(sapEndpointAuthURL);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuthObj.getUserId() + ":" + userAuthObj.getUserPass()).getBytes(StandardCharsets.UTF_8));
			request.addHeader(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			HttpResponse response = client.execute(request);
			if (response != null) {
				Integer responseCode = response.getStatusLine().getStatusCode();
				if (responseCode.equals(HttpURLConnection.HTTP_OK)
						|| (responseCode.equals(HttpURLConnection.HTTP_NO_CONTENT))) {
					return validateAuthFromHeader(userAuthObj, getSessionCookies(response.getAllHeaders()));
				} else if (responseCode.equals(HttpURLConnection.HTTP_UNAUTHORIZED)) {
					logger.info(HRSSConstantUtil.UNAUTHORISED_MSG);
					return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
				} else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) {
					logger.info(HRSSConstantUtil.BAD_REQUEST_MSG);
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				} else {
					logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
					return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
				}
			}
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<UserAuth> validateAuthFromHeader(UserAuth userAuthObj, List<String> authCookies) {
		if (authCookies.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		} else {
			String userId = cacheStoreClient.saveOrUpdate(userAuthObj.getUserId(), authCookies).getBody();
			return new ResponseEntity<UserAuth>(new UserAuth(userId, HRSSConstantUtil.EMPTY_STRING), HttpStatus.OK);
		}
	}

	public ResponseEntity<Object> deleteAuthToken(String employeeId) {
		return cacheStoreClient.delete(employeeId);
	}

	public ResponseEntity<List<String>> findAuthToken(String employeeId) {
		return cacheStoreClient.find(employeeId);
	}

	public ResponseEntity<InputStreamResource> getSapDocWithSSO(String url, String userId) {
		logger.info("SapEndpointUtil.getSapDocWithSSO()");
		File fileObj = new File(HRSSConstantUtil.DEFAULT_FILE_NAME + HRSSConstantUtil.PDF_FILE_TYPE);
		InputStreamResource resource = null;
		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet request = new HttpGet(url);
			UserAuth userAuthObj = getUserAuthObj(userId);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuthObj.getUserId() + ":" + userAuthObj.getUserPass()).getBytes(StandardCharsets.UTF_8));
			request.addHeader(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			HttpResponse response = client.execute(request);
			if (response != null) {
				Integer responseCode = response.getStatusLine().getStatusCode();
				if (responseCode.equals(HttpURLConnection.HTTP_OK)) {
					InputStream source = response.getEntity().getContent();
					FileUtils.copyInputStreamToFile(source, fileObj);
					resource = new InputStreamResource(new FileInputStream(fileObj));
				} else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) {
					logger.info(HRSSConstantUtil.BAD_REQUEST_MSG);
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				} else {
					logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
					return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
				}
			}
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						HRSSConstantUtil.HTTP_HEADER_FILE_ATTACHMENT + ";" + HRSSConstantUtil.HTTP_HEADER_FILE_NAME
								+ "=" + fileObj.getName())
				.contentType(MediaType.APPLICATION_PDF).contentLength(fileObj.length()).body(resource);
	}

	public String getSapResponseWithSSO(final String url, final String userId) {
		HttpURLConnection readConn = null;
		HttpURLConnection connection = null;
		BufferedReader in = null;
		try {
			logger.info("SapEndpointUtil.getSapResponseWithSSO()");
			final URL sapUrl = new URL(sapEndpointCSRFTokenURL);
			final UserAuth userAuth = getUserAuthObj(userId);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuth.getUserId() + ":" + userAuth.getUserPass()).getBytes(StandardCharsets.UTF_8));
			readConn = (HttpURLConnection) sapUrl.openConnection();
			readConn.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			readConn.setDoOutput(Boolean.TRUE);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN_PARAM);
			readConn.connect();

			final List<String> session = getSessionCookies(readConn.getHeaderFields());
			final String xsrfToken = extractXrsfToken(readConn);

			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_APPICATION_JSON);
			connection.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			connection.setDoOutput(Boolean.TRUE);

			String feed = null;
			if (connection != null) {
				Integer responseCode = connection.getResponseCode();
				if (responseCode.equals(HttpURLConnection.HTTP_OK)) {
					in = new BufferedReader(new InputStreamReader((InputStream) connection.getInputStream()));
					feed = in.readLine();
				} else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) {
					logger.info(HRSSConstantUtil.BAD_REQUEST_MSG);
				} else if (responseCode.equals(HttpURLConnection.HTTP_INTERNAL_ERROR)) {
					logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
				}
			}
			return feed;
		} catch (IOException e) {
			logger.log(Level.SEVERE, HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED, e);
			return null;
		} finally {
			if (readConn != null)
				readConn.disconnect();
			if (connection != null)
				connection.disconnect();
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	public Map<String, String> writeSapRequest(final String url, final String userId, final String jSONStr,
			final File uploadFile, final Boolean deleteFlag, final Boolean pmeFlag) {
		HttpURLConnection readConn = null;
		HttpURLConnection writeConn = null;
		BufferedReader in = null;
		try {
			logger.info("SapEndpointUtil.writeSapRequest()");
			final UserAuth userAuth = getUserAuthObj(userId);
			final Map<String, String> map = new HashMap<>();
			final URL csrfUrl = new URL(sapEndpointCSRFTokenURL);
			readConn = (HttpURLConnection) csrfUrl.openConnection();
			readConn.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuth.getUserId() + ":" + userAuth.getUserPass()).getBytes(StandardCharsets.UTF_8));
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN_PARAM);
			readConn.connect();

			final List<String> session = getSessionCookies(readConn.getHeaderFields());
			final String xsrfToken = extractXrsfToken(readConn);
			if (jSONStr != null && !jSONStr.isEmpty()) {
				if (!deleteFlag) {
					writeConn = postJSONToSap(url, writeConn, session, xsrfToken, jSONStr);
				}
			} else if (uploadFile != null) {
				writeConn = postMultipartFileToSap(url, writeConn, session, xsrfToken, uploadFile);
			} else {
				writeConn = deleteCallToSap(url, writeConn, session, xsrfToken);
			}

			if (writeConn != null) {
				final Integer responseCode = writeConn.getResponseCode();
				if (responseCode.equals(HttpURLConnection.HTTP_CREATED)) { // 201 Created
					if (pmeFlag) {
						setSAPPmeErrorMessageObj(writeConn, map);
					} else {
						map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_SUCCESS);
						map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, HRSSConstantUtil.EMPTY_STRING);
					}
				} else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) { // 400 Bad Request
					in = new BufferedReader(new InputStreamReader(writeConn.getErrorStream()));
					String inputLine = null;
					StringBuffer response = new StringBuffer();
					while ((inputLine = in.readLine()) != null) {
						response.append(inputLine);
					}
					SAPErrorMessage errObj = getErrorMessageObj(response.toString());
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_FAILED);
					if (errObj != null) {
						map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG,
								sapErrorHandlerUtil.getCustomErrorMsg(errObj.getErrCode(), errObj.getErrMessage()));
						map.put(HRSSConstantUtil.POST_RESPONSE_SYSTEM_ERR_MSG, errObj.getErrMessage());
					}
				} else if (responseCode.equals(HttpURLConnection.HTTP_INTERNAL_ERROR)) { // 500 Internal Server Error
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_ERROR);
					map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, HRSSConstantUtil.POST_RESPONSE_STATUS_ERROR_MSG);
				} else if (responseCode.equals(HttpURLConnection.HTTP_NO_CONTENT)) {
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_SUCCESS);
					map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, HRSSConstantUtil.EMPTY_STRING);
				}
			}
			return map;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			if (readConn != null)
				readConn.disconnect();
			if (writeConn != null)
				writeConn.disconnect();
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	private void setSAPPmeErrorMessageObj(HttpURLConnection conn, Map<String, String> map) {
		logger.info("SapEndpointUtil.writeSAPRequest.setSAPPmeErrorMessageObj()");
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine = null;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			SAPPmeErrorMessage errObj = getPMEErrorMessageObj(response.toString());
			if (errObj != null) {
				if (errObj.isStatus()) {
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_SUCCESS);
					map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG,
							errObj.getMessage().replaceAll(HRSSConstantUtil.LINE_FEED, HRSSConstantUtil.EMPTY_STRING));
				} else {
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_FAILED);
					map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, errObj.getMessage());
				}
			}
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}

	}

	private SAPPmeErrorMessage getPMEErrorMessageObj(String responseStr) {
		logger.info("SapEndpointUtil.writeSAPRequest.getPMEErrorMessageObj()");
		try {
			ObjectMapper objectMapper = objectMapperUtil.get();
			JsonNode rootNode = objectMapper.readTree(responseStr);
			return objectMapper.readValue(rootNode.get(HRSSConstantUtil.SAP_JSON_ROOT).toString(),
					new TypeReference<SAPPmeErrorMessage>() {
					});
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		}
	}

	private SAPErrorMessage getErrorMessageObj(final String responseStr) {
		logger.info("SapEndpointUtil.writeSAPRequest.getErrorMessageObj()");
		try {
			ObjectMapper objectMapper = objectMapperUtil.get();
			JsonNode rootNode = objectMapper.readTree(responseStr);
			return objectMapper.readValue(
					rootNode.get(HRSSConstantUtil.SAP_ERROR_ROOT).get(HRSSConstantUtil.SAP_INNER_ERROR)
							.get(HRSSConstantUtil.SAP_ERROR_DETAILS).get(HRSSConstantUtil.ZERO.intValue()).toString(),
					new TypeReference<SAPErrorMessage>() {
					});
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		}
	}

	private HttpURLConnection postJSONToSap(final String url, HttpURLConnection writeConn, final List<String> session,
			final String xsrfToken, final String jSONStr) {
		logger.info("SapEndpointUtil.postJSONToSap()");
		OutputStream os = null;
		try {
			writeConn = (HttpURLConnection) new URL(url).openConnection();
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_ACCEPT, HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestMethod(HRSSConstantUtil.HTTP_POST_REQUEST);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			writeConn.setDoOutput(Boolean.TRUE);
			os = writeConn.getOutputStream();
			os.write(jSONStr.getBytes());
			writeConn.connect();
			return writeConn;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			try {
				if (os != null) {
					os.flush();
					os.close();
				}
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	private HttpURLConnection postMultipartFileToSap(final String url, HttpURLConnection writeConn,
			final List<String> session, final String xsrfToken, final File uploadFile) {
		logger.info("SapEndpointUtil.postMultipartFileToSap()");
		OutputStream os = null;
		try {
			writeConn = (HttpURLConnection) new URL(url).openConnection();
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_MULTIPART_DATA);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_ACCEPT, HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestMethod(HRSSConstantUtil.HTTP_POST_REQUEST);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			writeConn.setDoInput(Boolean.TRUE);
			writeConn.setDoOutput(Boolean.TRUE);
			os = writeConn.getOutputStream();
			os.write(uploadFile.toString().getBytes());
			writeConn.connect();
			return writeConn;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			try {
				if (os != null) {
					os.flush();
					os.close();
				}
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	public Map<String, String> updateSapRequest(final String url, final String userId, final String jSONStr,
			final Boolean pmeFlag) {
		HttpURLConnection readConn = null;
		HttpURLConnection writeConn = null;
		BufferedReader in = null;
		try {
			logger.info("SapEndpointUtil.writeSapRequest()");
			final UserAuth userAuth = getUserAuthObj(userId);
			final Map<String, String> map = new HashMap<>();
			final URL csrfUrl = new URL(sapEndpointCSRFTokenURL);
			readConn = (HttpURLConnection) csrfUrl.openConnection();
			readConn.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuth.getUserId() + ":" + userAuth.getUserPass()).getBytes(StandardCharsets.UTF_8));
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN_PARAM);
			readConn.connect();

			final List<String> session = getSessionCookies(readConn.getHeaderFields());
			final String xsrfToken = extractXrsfToken(readConn);
			if (jSONStr != null && !jSONStr.isEmpty()) {
				writeConn = putJSONToSap(url, writeConn, session, xsrfToken, jSONStr);
			}

			if (writeConn != null) {
				final Integer responseCode = writeConn.getResponseCode();
				if (responseCode.equals(HttpURLConnection.HTTP_ACCEPTED)
						|| responseCode.equals(HttpURLConnection.HTTP_NO_CONTENT)) { // 202 Accepted
					if (pmeFlag) {
						setSAPPmeErrorMessageObj(writeConn, map);
					} else {
						map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_SUCCESS);
						map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, HRSSConstantUtil.EMPTY_STRING);
					}
				} else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) { // 400 Bad Request
					in = new BufferedReader(new InputStreamReader(writeConn.getErrorStream()));
					String inputLine = null;
					StringBuffer response = new StringBuffer();
					while ((inputLine = in.readLine()) != null) {
						response.append(inputLine);
					}
					SAPErrorMessage errObj = getErrorMessageObj(response.toString());
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_FAILED);
					if (errObj != null) {
						map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG,
								sapErrorHandlerUtil.getCustomErrorMsg(errObj.getErrCode(), errObj.getErrMessage()));
						map.put(HRSSConstantUtil.POST_RESPONSE_SYSTEM_ERR_MSG, errObj.getErrMessage());
					}
				} else if (responseCode.equals(HttpURLConnection.HTTP_INTERNAL_ERROR)) { // 500 Internal Server Error
					map.put(HRSSConstantUtil.POST_RESPONSE_STATUS, HRSSConstantUtil.POST_RESPONSE_STATUS_ERROR);
					map.put(HRSSConstantUtil.POST_RESPONSE_ERR_MSG, HRSSConstantUtil.POST_RESPONSE_STATUS_ERROR_MSG);
				}
			}
			return map;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			if (readConn != null)
				readConn.disconnect();
			if (writeConn != null)
				writeConn.disconnect();
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	private HttpURLConnection putJSONToSap(final String url, HttpURLConnection writeConn, final List<String> session,
			final String xsrfToken, final String jSONStr) {
		logger.info("SapEndpointUtil.postJSONToSap()");
		OutputStream os = null;
		try {
			writeConn = (HttpURLConnection) new URL(url).openConnection();
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_ACCEPT, HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestMethod(HRSSConstantUtil.HTTP_PUT_REQUEST);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			writeConn.setDoOutput(Boolean.TRUE);
			os = writeConn.getOutputStream();
			os.write(jSONStr.getBytes());
			writeConn.connect();
			return writeConn;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			try {
				if (os != null) {
					os.flush();
					os.close();
				}
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	private HttpURLConnection deleteCallToSap(final String url, HttpURLConnection writeConn, final List<String> session,
			final String xsrfToken) {
		logger.info("SapEndpointUtil.deleteJSONToSap()");
		OutputStream os = null;
		try {
			writeConn = (HttpURLConnection) new URL(url).openConnection();
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_ACCEPT, HRSSConstantUtil.HTTP_APPICATION_JSON);
			writeConn.setRequestMethod(HRSSConstantUtil.HTTP_DELETE_REQUEST);
			writeConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			writeConn.setDoOutput(Boolean.TRUE);
			writeConn.connect();
			return writeConn;
		} catch (IOException e) {
			logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			return null;
		} finally {
			try {
				if (os != null) {
					os.flush();
					os.close();
				}
			} catch (IOException e) {
				logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
			}
		}
	}

	private List<String> getSessionCookies(final Header[] headerArr) {
		logger.info("SapEndpointUtil.getSessionCookies()");
		List<String> authCookieList = new ArrayList<>();
		Map<String, List<Header>> cookieHeaderMap = Arrays.asList(headerArr).parallelStream()
				.collect(Collectors.groupingBy(Header::getName));
		List<Header> authCookieHeaderList = cookieHeaderMap.get(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_SET_COOKIE_CAPS);
		if (authCookieHeaderList != null) {
			for (Header obj : authCookieHeaderList) {
				authCookieList.add(obj.getValue());
			}
		}
		return authCookieList;
	}

	private List<String> getSessionCookies(final Map<String, List<String>> responseHeaders) {
		logger.info("SapEndpointUtil.getSessionCookies()");
		Iterator<String> keys = responseHeaders.keySet().iterator();
		String key;
		while (keys.hasNext()) {
			key = keys.next();
			if (HRSSConstantUtil.HTTP_REQUEST_PROPERTY_SET_COOKIE.equalsIgnoreCase(key)) {
				List<String> session = responseHeaders.get(key);
				return session.stream()
						.filter(e -> e.contains(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_SET_COOKIE_SESSIONID)
								|| e.contains(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_SET_COOKIE_SSO2))
						.collect(Collectors.toList());
			}
		}
		return new ArrayList<>();
	}

	private String setTokenCookies(final List<String> session) {
		logger.info("SapEndpointUtil.setTokenCookies()");
		if (session != null) {
			String agregated_cookies = HRSSConstantUtil.EMPTY_STRING;
			for (String cookie : session) {
				agregated_cookies += cookie + "; ";
			}
			return agregated_cookies.replaceAll(HRSSConstantUtil.HTTP_HEADER_REGEX_MANIPULATION,
					HRSSConstantUtil.EMPTY_STRING);
		}
		return HRSSConstantUtil.EMPTY_STRING;
	}

	private String extractXrsfToken(final HttpURLConnection conn) {
		logger.info("SapEndpointUtil.extractXrsfToken()");
		List<String> value = null;
		final Map<String, List<String>> headers = conn.getHeaderFields();
		final Iterator<String> keys = headers.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			if (HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN.equalsIgnoreCase(key)) {
				value = headers.get(key);
			}
		}
		if (value == null || value.size() == HRSSConstantUtil.ZERO.intValue()) {
			return null;
		} else {
			return value.get(HRSSConstantUtil.ZERO.intValue());
		}
	}

	/* PDF FILEVIEW */
	public void getSapPdfViewResponse(HttpServletResponse response, String url, String userId, String fileName)
			throws IOException {

		logger.info("SapEndpointUtil.getSapPdfViewResponse()");
		getSapPdf(response, url, userId, fileName, false);

	}

	// PDF DOWNLOAD
	public void getSapPdfDownload(HttpServletResponse response, String url, String userId, String fileName)
			throws IOException {

		logger.info("SapEndpointUtil.getSapPdfDownload()");
		getSapPdf(response, url, userId, fileName, true);

	}

	// PDF TEMPLATE
	private void getSapPdf(HttpServletResponse response, String url, String userId, String fileName, boolean download)
			throws IOException {

		HttpURLConnection readConn = null;
		HttpURLConnection connection = null;
		BufferedInputStream bufferedInputStream = null;
		BufferedOutputStream bufferedOutputStream = null;

		try {
			logger.info("SapEndpointUtil.pdfResponseTemplate()");

			if (download) {
				// File view
				response.setHeader(HRSSConstantUtil.HTTP_HEADER_CONTENT_DISPOSITION,
						HRSSConstantUtil.HTTP_HEADER_CONTENT_DISPOSITION_ATTACH_FILE + fileName
								+ HRSSConstantUtil.PDF_FILE_TYPE);
			} else {
				response.setHeader(HRSSConstantUtil.HTTP_HEADER_CONTENT_DISPOSITION,
						HRSSConstantUtil.HTTP_HEADER_CONTENT_DISPOSITION_VIEW_FILE + fileName);
			}

			final URL sapUrl = new URL(sapEndpointCSRFTokenURL);
			final UserAuth userAuth = getUserAuthObj(userId);
			final String encoding = Base64.getEncoder().encodeToString(
					(userAuth.getUserId() + ":" + userAuth.getUserPass()).getBytes(StandardCharsets.UTF_8));
			readConn = (HttpURLConnection) sapUrl.openConnection();
			readConn.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			readConn.setDoOutput(Boolean.TRUE);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_AUTH_BASIC + encoding);
			readConn.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN,
					HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN_PARAM);
			readConn.connect();

			final List<String> session = getSessionCookies(readConn.getHeaderFields());
			final String xsrfToken = extractXrsfToken(readConn);

			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_CSRF_TOKEN, xsrfToken);
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_CONTENT_TYPE,
					HRSSConstantUtil.HTTP_APPICATION_JSON);
			connection.setRequestMethod(HRSSConstantUtil.HTTP_GET_REQUEST);
			connection.setRequestProperty(HRSSConstantUtil.HTTP_REQUEST_PROPERTY_COOKIE, setTokenCookies(session));
			connection.setDoOutput(Boolean.TRUE);

			if (connection != null) {
				Integer responseCode = connection.getResponseCode();
				if (responseCode.equals(HttpURLConnection.HTTP_OK)) {
					bufferedInputStream = new BufferedInputStream(connection.getInputStream());

					// OutPut Stream
					bufferedOutputStream = new BufferedOutputStream(response.getOutputStream());

					// Create Byte array to store file content
					byte[] content = new byte[connection.getContentLength()];

					int data;
					int i = 0;
					while ((data = bufferedInputStream.read()) != -1) {
						content[i] = (byte) data;
						i++;
					}

					// ContentType PDF format to Response Object
					response.setContentType(HRSSConstantUtil.HTTP_APPICATION_PDF);
					response.setHeader(HRSSConstantUtil.HTTP_HEADER_CACHE_CONTROL,
							HRSSConstantUtil.HTTP_HEADER_CACHE_CONTROL_REVALIDATE);
					response.setHeader(HRSSConstantUtil.HTTP_HEADER_PRAGMA, HRSSConstantUtil.HTTP_HEADER_PRAGMA_PUBLIC);

					// content write
					bufferedOutputStream.write(content);
				}

				else if (responseCode.equals(HttpURLConnection.HTTP_BAD_REQUEST)) {
					response.sendError(400, "Reuested Form16 is not available for " + fileName);
					logger.info(HRSSConstantUtil.BAD_REQUEST_MSG);
				} else if (responseCode.equals(HttpURLConnection.HTTP_INTERNAL_ERROR)) {
					response.sendError(500, "Reuested Form16 is not available for " + fileName);
					logger.info(HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED);
				}
			}

		} catch (IOException e) {
			logger.log(Level.SEVERE, HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED, e);

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
			try {
				if (bufferedInputStream != null) {
					bufferedInputStream.close();
				}
				if (bufferedOutputStream != null) {
					bufferedOutputStream.flush();
					bufferedOutputStream.close();
				}
			} catch (IOException e) {
				logger.log(Level.SEVERE, HRSSConstantUtil.UNEXPECTED_ERROR_OCCURRED, e);
			}
		}
	}

	public Map<String, UserAuth> getUsermap() {
		return userMap;
	}

	private UserAuth getUserAuthObj(String userId) {
		logger.info("SapEndpointUtil.getUserAuthObj()");
		if (userId != null && !userId.isEmpty()) {
			return userMap.get(userId);
		} else {
			return userMap.get(HRSSConstantUtil.DEFAULT_USER_KEY);
		}
	}

	private Map<String, UserAuth> setUserMap() {
		final Map<String, UserAuth> map = new HashMap<>();
		map.put(HRSSConstantUtil.DEFAULT_USER_KEY, new UserAuth("P19505433", "Fiori@430"));
		map.put("P19505433", new UserAuth("P19505433", "Fiori@430"));
		map.put("P30503557", new UserAuth("P30503557", "Jio@222"));
		map.put("P30503555", new UserAuth("P30503555", "Jio@111"));
		map.put("P30502951", new UserAuth("P30502951", "Jioo@03"));
		map.put("P10025204", new UserAuth("P10025204", "Jio@2018"));
		map.put("P30503556", new UserAuth("P30503556", "Jio@430"));
		map.put("P50008082", new UserAuth("P50008082", "Jio@111"));
		map.put("P50002931", new UserAuth("P50002931", "Jio@111"));
		map.put("P50008082", new UserAuth("P50008082", "Jio@111"));
		map.put("P10029284", new UserAuth("P10029284", "Mumbai@01"));
		map.put("P00575504", new UserAuth("P00575504", "Jio@111"));
		map.put("P10036243", new UserAuth("P10036243", "Jio@111"));
		map.put("P30502947", new UserAuth("P30502947", "Jio@111"));
		map.put("P16243160", new UserAuth("P16243160", "Jio@111"));
		map.put("P00206101", new UserAuth("P00206101", "Jio@111"));
		map.put("P30503240", new UserAuth("P30503240", "Jio@111"));
		map.put("P19507440", new UserAuth("P19507440", "Fiori@430"));
		map.put("P00575504", new UserAuth("P00575504", "Jio@430"));
		map.put("P10002615", new UserAuth("P10002615", "Jio@430"));
		map.put("P10035967", new UserAuth("P10035967", "Jio@430"));
		map.put("P10035892", new UserAuth("P10035892", "Jio@430"));
		map.put("P10035897", new UserAuth("P10035897", "Jio@430"));
		map.put("P10028688", new UserAuth("P10028688", "Jio@430"));
		map.put("P10035905", new UserAuth("P10035905", "Jio@430"));
		map.put("P10036020", new UserAuth("P10036020", "Jio@430"));
		map.put("P10036060", new UserAuth("P10036060", "Jio@430"));
		map.put("P10036158", new UserAuth("P10036158", "Jio@430"));
		map.put("P10036236", new UserAuth("P10036236", "Jio@430"));
		map.put("P10036081", new UserAuth("P10036081", "Jio@430"));
		map.put("P10036082", new UserAuth("P10036082", "Jio@430"));
		map.put("P10036083", new UserAuth("P10036083", "Jio@430"));
		map.put("P10036084", new UserAuth("P10036084", "Jio@430"));
		map.put("P10036650", new UserAuth("P10036650", "Charan@990"));
		map.put("P10031309", new UserAuth("P10031309", "Charan@990"));
		map.put("P10031313", new UserAuth("P10031313", "Charan@990"));
		map.put("P10031355", new UserAuth("P10031355", "Charan@990"));
		map.put("P10031556", new UserAuth("P10031556", "Charan@990"));
		map.put("P10031368", new UserAuth("P10031368", "Charan@990"));
		map.put("P10031533", new UserAuth("P10031533", "Charan@990"));
		map.put("P10031545", new UserAuth("P10031545", "Charan@990"));
		map.put("P10035786", new UserAuth("P10035786", "JIO@4300"));
		map.put("P10035783", new UserAuth("P10035783", "JIO@4300"));
		map.put("P00403903", new UserAuth("P00403903", "Smp@1234"));
		map.put("P50008082", new UserAuth("P50008082", "Jio@111"));
		map.put("P10036789", new UserAuth("P10036789", "Fiori@430"));
		return map;
	}

}
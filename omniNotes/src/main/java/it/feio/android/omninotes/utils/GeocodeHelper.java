/*
 * Copyright (C) 2015 Federico Iosue (federico.iosue@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.feio.android.omninotes.utils;

import android.content.Context;
import android.location.*;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.models.listeners.OnGeoUtilResultListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GeocodeHelper implements LocationListener {

	private static final String LOG_TAG = Constants.TAG;
	private static final String PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";
	private static final String TYPE_AUTOCOMPLETE = "/autocomplete";
	private static final String OUT_JSON = "/json";
	private static final String API_KEY = "AIzaSyBq_nZEz9sZMwEJ28qmbg20CFG1Xo1JGp0";

	private static GeocodeHelper instance;
	private static LocationManager locationManager;


	private GeocodeHelper() {
		instance = this;
		locationManager = (LocationManager) OmniNotes.getAppContext().getSystemService(Context.LOCATION_SERVICE);
	}


	public static void startListening() {
		if (instance == null) {
			instance = new GeocodeHelper();
		}
		String provider = locationManager.getBestProvider(new Criteria(), true);
		provider = TextUtils.isEmpty(provider) ? LocationManager.PASSIVE_PROVIDER : provider;
		locationManager.requestLocationUpdates(provider, 60000, 50, instance, null);
	}


	public static void stopListening() {
		if (locationManager != null) {
			locationManager.removeUpdates(instance);
		}
	}


	@Override
	public void onLocationChanged(Location newLocation) {
	}


	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}


	@Override
	public void onProviderEnabled(String provider) {
	}


	@Override
	public void onProviderDisabled(String provider) {
	}


	public static Location getLastKnowLocation() {
		if (locationManager == null) {
			throw new NullPointerException("Call 'startListening' before!");
		}
		String provider = locationManager.getBestProvider(new Criteria(), true);
		if (provider == null) {
			return null;
		}
		return locationManager.getLastKnownLocation(provider);
	}


	public static String getAddressFromCoordinates(Context mContext, double latitude,
												   double longitude) throws IOException {
		String addressString = "";
		Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
		List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
		if (addresses.size() > 0) {
			Address address = addresses.get(0);
			if (address != null) {
				addressString = address.getThoroughfare() + ", " + address.getLocality();
			}
		}
		return addressString;
	}


	public static void getAddressFromCoordinates(Context mContext, double latitude, double longitude,
												 final OnGeoUtilResultListener listener) {
		class AddressResolverTask extends AsyncTask<Double, Void, String> {

			private Context mContext;


			public AddressResolverTask(Context context) {
				this.mContext = context;
			}


			@Override
			protected String doInBackground(Double... params) {
				String addressString;
				try {
					addressString = GeocodeHelper.getAddressFromCoordinates(this.mContext, params[0], params[1]);
				} catch (IOException ex) {
					addressString = null;
				}
				return addressString;
			}


			@Override
			protected void onPostExecute(String result) {
				super.onPostExecute(result);
				listener.onAddressResolved(result);
			}
		}

		AddressResolverTask task = new AddressResolverTask(mContext);
		if (Build.VERSION.SDK_INT >= 11) {
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, latitude, longitude);
		} else {
			task.execute(latitude, longitude);
		}
	}


	public static double[] getCoordinatesFromAddress(Context mContext, String address)
			throws IOException {
		double[] result = new double[2];
		Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
		List<Address> addresses = geocoder.getFromLocationName(address, 1);
		if (addresses.size() > 0) {
			double latitude = addresses.get(0).getLatitude();
			double longitude = addresses.get(0).getLongitude();
			result[0] = latitude;
			result[1] = longitude;
		}
		return result;
	}


	public static void getCoordinatesFromAddress(Context mContext, String address,
												 final OnGeoUtilResultListener listener) {
		class CoordinatesResolverTask extends AsyncTask<String, Void, double[]> {

			private Context mContext;


			public CoordinatesResolverTask(Context context) {
				this.mContext = context;
			}


			@Override
			protected double[] doInBackground(String... params) {
				double[] coords;
				try {
					coords = GeocodeHelper.getCoordinatesFromAddress(mContext, params[0]);
				} catch (IOException ex) {
					coords = null;
				}
				return coords;
			}


			@Override
			protected void onPostExecute(double[] coords) {
				super.onPostExecute(coords);
				listener.onCoordinatesResolved(coords);
			}
		}

		CoordinatesResolverTask task = new CoordinatesResolverTask(mContext);
		if (Build.VERSION.SDK_INT >= 11) {
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, address);
		} else {
			task.execute(address);
		}
	}


	public static ArrayList<String> autocomplete(String input) {
		ArrayList<String> resultList = null;

		HttpURLConnection conn = null;
		StringBuilder jsonResults = new StringBuilder();
		try {
			URL url = new URL(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON + "?key=" + API_KEY + "&input=" +
					URLEncoder.encode(input, "utf8"));
			conn = (HttpURLConnection) url.openConnection();
			InputStreamReader in = new InputStreamReader(conn.getInputStream());

			// Load the results into a StringBuilder
			int read;
			char[] buff = new char[1024];
			while ((read = in.read(buff)) != -1) {
				jsonResults.append(buff, 0, read);
			}
		} catch (MalformedURLException e) {
			Log.e(Constants.TAG, "Error processing Places API URL");
			return resultList;
		} catch (IOException e) {
			Log.e(Constants.TAG, "Error connecting to Places API");
			return resultList;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		try {
			// Create a JSON object hierarchy from the results
			JSONObject jsonObj = new JSONObject(jsonResults.toString());
			JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

			// Extract the Place descriptions from the results
			resultList = new ArrayList<>(predsJsonArray.length());
			for (int i = 0; i < predsJsonArray.length(); i++) {
				resultList.add(predsJsonArray.getJSONObject(i).getString("description"));
			}
		} catch (JSONException e) {
			Log.e(Constants.TAG, "Cannot process JSON results", e);
		}

		return resultList;
	}


	public static boolean notCoordinates(String string) {
		Pattern p = Pattern.compile("^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|" +
				"([1-9]?\\d))(\\.\\d+)?)$");
		Matcher m = p.matcher(string);
		return m.matches();
	}
}

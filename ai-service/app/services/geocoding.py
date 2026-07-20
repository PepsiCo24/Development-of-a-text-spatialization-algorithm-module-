import time
from threading import Lock

import httpx

from app.core.config import Settings, get_settings
from app.models.spatial import GeoJsonGeometry


class PlaceGeocoder:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None) -> None:
        self.settings = settings or get_settings()
        self.client = client or httpx.Client(timeout=self.settings.geocoding_timeout_seconds)
        self._cache: dict[str, GeoJsonGeometry | None] = {}
        self._request_lock = Lock()
        self._last_request_at = 0.0

    def geocode(self, query: str) -> GeoJsonGeometry | None:
        normalized_query = query.strip()
        if not self.settings.geocoding_enabled or not normalized_query:
            return None
        if normalized_query in self._cache:
            return self._cache[normalized_query]
        try:
            with self._request_lock:
                wait_seconds = self.settings.geocoding_min_interval_seconds - (time.monotonic() - self._last_request_at)
                if wait_seconds > 0:
                    time.sleep(wait_seconds)
                response = self.client.get(
                    f"{self.settings.geocoding_base_url.rstrip('/')}/search",
                    params={"q": normalized_query, "format": "jsonv2", "limit": 1},
                    headers={"User-Agent": self.settings.geocoding_user_agent},
                )
                self._last_request_at = time.monotonic()
            response.raise_for_status()
            rows = response.json()
            if not rows:
                self._cache[normalized_query] = None
                return None
            geometry = GeoJsonGeometry(type="Point", coordinates=[float(rows[0]["lon"]), float(rows[0]["lat"])])
            self._cache[normalized_query] = geometry
            return geometry
        except (httpx.HTTPError, KeyError, TypeError, ValueError):
            return None

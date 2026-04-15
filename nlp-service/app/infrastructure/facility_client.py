import httpx
from typing import Optional
import logging

logger = logging.getLogger(__name__)

class FacilityServiceClient:
    def __init__(self, base_url: str):
        self.base_url = base_url

    async def resolve_facility_id(
        self, facility_name: str, jwt_token: str
    ) -> Optional[str]:
        for attempt in range(2):
            try:
                async with httpx.AsyncClient() as client:
                    response = await client.get(
                        f"{self.base_url}/api/v1/facilities/lookup/batch",
                        params={"names": [facility_name]},
                        headers={"Authorization": f"Bearer {jwt_token}"},
                        timeout=5.0,
                    )
                    if response.status_code == 200:
                        facilities = response.json()
                        if facilities:
                            return facilities[0]["facilityId"]
            except Exception as e:
                logger.warning(f"Facility lookup attempt {attempt + 1} failed: {e}")
                continue  # ← add this line
        return None
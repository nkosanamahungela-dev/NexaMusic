from types import SimpleNamespace
from unittest import IsolatedAsyncioTestCase
from unittest.mock import patch

from fastapi import HTTPException

from app import main as service


def make_track(name: str = "Test Song"):
    return SimpleNamespace(
        name=name,
        uri="spotify:track:123",
        duration=SimpleNamespace(total_milliseconds=185000),
        artists=SimpleNamespace(
            items=[
                SimpleNamespace(profile=SimpleNamespace(name="Test Artist")),
            ]
        ),
        album_of_track=SimpleNamespace(
            name="Test Album",
            cover_art=SimpleNamespace(
                sources=[SimpleNamespace(url="https://images.example/cover.jpg")]
            ),
        ),
    )


class SpotApiAdapterTests(IsolatedAsyncioTestCase):
    def test_serialize_track_uses_spotapi_v2_model_shape(self):
        result = service._serialize_track(make_track())

        self.assertEqual(result["name"], "Test Song")
        self.assertEqual(result["uri"], "spotify:track:123")
        self.assertEqual(result["duration_ms"], 185000)
        self.assertEqual(result["artists"], ["Test Artist"])
        self.assertEqual(result["album"], "Test Album")
        self.assertEqual(result["image_url"], "https://images.example/cover.jpg")

    async def test_search_reads_async_pages_and_honors_limit(self):
        async def fake_search(_query):
            yield [make_track("First"), make_track("Second")]

        with patch.object(service.Public, "search_tracks", new=fake_search):
            result = await service._search("tracks", "test", 1)

        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["name"], "First")

    async def test_search_translates_upstream_failure_to_502(self):
        async def failing_search(_query):
            raise RuntimeError("upstream unavailable")
            yield  # Make this an async generator.

        with patch.object(service.Public, "search_tracks", new=failing_search):
            with self.assertRaises(HTTPException) as raised:
                await service._search("tracks", "test", 5)

        self.assertEqual(raised.exception.status_code, 502)


if __name__ == "__main__":
    import unittest

    unittest.main()

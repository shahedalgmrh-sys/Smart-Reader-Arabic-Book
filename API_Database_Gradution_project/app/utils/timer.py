import time
import logging
from typing import Optional, Dict, Any

logger = logging.getLogger("app.timer")

class PrecisionTimer:
    """
    A precision timer for measuring code execution time.
    Supports checkpoints to measure different stages.
    """
    def __init__(self, name: str = "Task"):
        self.name = name
        self.start_time: Optional[float] = None
        self.end_time: Optional[float] = None
        self.checkpoints: Dict[str, float] = {}
        self._last_checkpoint_time: Optional[float] = None

    def start(self):
        """Start the timer."""
        self.start_time = time.perf_counter()
        self._last_checkpoint_time = self.start_time
        logger.info(f"[{self.name}] Started")
        return self

    def stop(self) -> float:
        """Stop the timer and return total duration in seconds."""
        self.end_time = time.perf_counter()
        if self.start_time is None:
            return 0.0
        duration = self.end_time - self.start_time
        logger.info(f"[{self.name}] Finished in {duration:.4f}s")
        return duration

    def checkpoint(self, label: str) -> float:
        """Record a checkpoint and return time since last checkpoint."""
        now = time.perf_counter()
        if self._last_checkpoint_time is None:
            self._last_checkpoint_time = self.start_time or now
        
        duration = now - self._last_checkpoint_time
        self.checkpoints[label] = duration
        self._last_checkpoint_time = now
        logger.info(f"[{self.name}] Checkpoint '{label}': {duration:.4f}s")
        return duration

    @property
    def total_time(self) -> float:
        if self.start_time is None:
            return 0.0
        end = self.end_time or time.perf_counter()
        return end - self.start_time

    def get_stats(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "total_time": self.total_time,
            "checkpoints": self.checkpoints
        }

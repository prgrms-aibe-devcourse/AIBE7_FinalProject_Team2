""".env 파일 로더 테스트 (BE-86). 네트워크 · 실제 파일 시스템 쓰기(임시 디렉터리 제외)를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import os
import stat
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import env_file  # noqa: E402


class ParseEnvTextTest(unittest.TestCase):
    def test_basic_key_value(self):
        self.assertEqual(env_file.parse_env_text("OPENAI_API_KEY=sk-123"), {"OPENAI_API_KEY": "sk-123"})

    def test_comments_and_blank_lines_are_skipped(self):
        text = "\n# 주석\nKEY=value\n\n  # 들여쓴 주석\n"
        self.assertEqual(env_file.parse_env_text(text), {"KEY": "value"})

    def test_export_prefix_is_stripped(self):
        self.assertEqual(env_file.parse_env_text("export GROQ_API_KEY=abc"), {"GROQ_API_KEY": "abc"})

    def test_quoted_values_are_unwrapped(self):
        text = 'A="hello world"\nB=\'single quoted\'\nC=no-quotes'
        self.assertEqual(env_file.parse_env_text(text), {"A": "hello world", "B": "single quoted", "C": "no-quotes"})

    def test_mismatched_quotes_are_kept_as_is(self):
        self.assertEqual(env_file.parse_env_text('A="unterminated'), {"A": '"unterminated'})

    def test_whitespace_around_key_and_value_is_trimmed(self):
        self.assertEqual(env_file.parse_env_text("  KEY  =  value  "), {"KEY": "value"})

    def test_value_can_contain_equals_signs(self):
        self.assertEqual(env_file.parse_env_text("DB_URL=postgresql://x?a=1&b=2"),
                         {"DB_URL": "postgresql://x?a=1&b=2"})

    def test_line_without_equals_is_skipped(self):
        self.assertEqual(env_file.parse_env_text("NOT_A_KV_LINE\nKEY=value"), {"KEY": "value"})

    def test_empty_key_is_skipped(self):
        self.assertEqual(env_file.parse_env_text("=value\nKEY=value"), {"KEY": "value"})

    def test_empty_value_is_allowed(self):
        self.assertEqual(env_file.parse_env_text("KEY="), {"KEY": ""})

    def test_later_duplicate_key_wins_within_one_file(self):
        self.assertEqual(env_file.parse_env_text("A=1\nA=2"), {"A": "2"})


class ParseEnvFileTest(unittest.TestCase):
    def test_missing_file_returns_empty_dict(self):
        self.assertEqual(env_file.parse_env_file(Path("/no/such/path/.env")), {})

    def test_reads_file_from_disk(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("OPENAI_API_KEY=sk-test\n", encoding="utf-8")
            self.assertEqual(env_file.parse_env_file(path), {"OPENAI_API_KEY": "sk-test"})

    def test_bad_line_warns_but_keeps_reading(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("GOOD=1\nBROKEN_LINE\nALSO_GOOD=2\n", encoding="utf-8")
            messages = []
            result = env_file.parse_env_file(path, log=messages.append)
            self.assertEqual(result, {"GOOD": "1", "ALSO_GOOD": "2"})
            self.assertTrue(any("BROKEN_LINE" in m for m in messages))

    @unittest.skipUnless(hasattr(stat, "S_IROTH"), "POSIX 전용 권한 검사")
    def test_world_readable_file_warns(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("A=1\n", encoding="utf-8")
            path.chmod(0o644)  # 다른 사용자도 읽을 수 있다
            messages = []
            env_file.parse_env_file(path, log=messages.append)
            self.assertTrue(any("권한" in m for m in messages))

    @unittest.skipUnless(hasattr(stat, "S_IROTH"), "POSIX 전용 권한 검사")
    def test_owner_only_file_does_not_warn(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("A=1\n", encoding="utf-8")
            path.chmod(0o600)  # 소유자만 읽기 · 쓰기
            messages = []
            env_file.parse_env_file(path, log=messages.append)
            self.assertFalse(any("권한" in m for m in messages))


class ApplyEnvFilesTest(unittest.TestCase):
    def test_sets_unset_variables(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("NEW_VAR=from-env-file\n", encoding="utf-8")
            with mock.patch.dict("os.environ", {}, clear=True):
                applied = env_file.apply_env_files(paths=[path])
                self.assertEqual(applied, ["NEW_VAR"])
                self.assertEqual(os.environ["NEW_VAR"], "from-env-file")

    def test_existing_environment_variable_wins(self):
        """이미 설정된 값은 .env가 덮지 않는다 — 한 번만 다른 키로 돌릴 때를 위해서다."""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("OPENAI_API_KEY=from-file\n", encoding="utf-8")
            with mock.patch.dict("os.environ", {"OPENAI_API_KEY": "from-shell"}, clear=True):
                applied = env_file.apply_env_files(paths=[path])
                self.assertEqual(applied, [])
                self.assertEqual(os.environ["OPENAI_API_KEY"], "from-shell")

    def test_first_path_wins_over_later_path(self):
        """tools/ai-judgment/.env가 저장소 루트 .env보다 먼저 읽히고 이긴다."""
        with tempfile.TemporaryDirectory() as tmp:
            first = Path(tmp) / "first.env"
            second = Path(tmp) / "second.env"
            first.write_text("KEY=from-first\n", encoding="utf-8")
            second.write_text("KEY=from-second\nOTHER=only-in-second\n", encoding="utf-8")
            with mock.patch.dict("os.environ", {}, clear=True):
                env_file.apply_env_files(paths=[first, second])
                self.assertEqual(os.environ["KEY"], "from-first")
                self.assertEqual(os.environ["OTHER"], "only-in-second")

    def test_missing_files_are_silently_skipped(self):
        with mock.patch.dict("os.environ", {}, clear=True):
            applied = env_file.apply_env_files(paths=[Path("/no/such/.env")])
        self.assertEqual(applied, [])

    def test_notice_lists_names_only_not_values(self):
        """불러온 변수는 이름만 안내한다. 값은 어디에도 남기지 않는다 (키 유출 방지)."""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("OPENAI_API_KEY=sk-super-secret-value\n", encoding="utf-8")
            messages = []
            with mock.patch.dict("os.environ", {}, clear=True):
                env_file.apply_env_files(paths=[path], log=messages.append)
            notice = next(m for m in messages if "OPENAI_API_KEY" in m)
            self.assertNotIn("sk-super-secret-value", notice)

    def test_no_notice_when_nothing_applied(self):
        with mock.patch.dict("os.environ", {}, clear=True):
            messages = []
            env_file.apply_env_files(paths=[Path("/no/such/.env")], log=messages.append)
        self.assertEqual(messages, [])


class EnsureLoadedTest(unittest.TestCase):
    def setUp(self):
        env_file.reset_loaded()
        self.addCleanup(env_file.reset_loaded)

    def test_runs_once_per_process(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("ONCE_VAR=1\n", encoding="utf-8")
            with mock.patch.object(env_file, "DEFAULT_ENV_PATHS", (path,)):
                with mock.patch.dict("os.environ", {}, clear=True):
                    first = env_file.ensure_loaded()
                    os.environ.pop("ONCE_VAR", None)  # 다시 호출해도 .env를 또 읽지 않는지 확인
                    second = env_file.ensure_loaded()
        self.assertEqual(first, ["ONCE_VAR"])
        self.assertEqual(second, [])  # 두 번째는 이미 적용됐다고 보고 아무것도 하지 않는다

    def test_reset_loaded_allows_reapplying(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("AGAIN=1\n", encoding="utf-8")
            with mock.patch.object(env_file, "DEFAULT_ENV_PATHS", (path,)):
                with mock.patch.dict("os.environ", {}, clear=True):
                    env_file.ensure_loaded()
                    os.environ.pop("AGAIN", None)  # reset_loaded 효과만 보려고 적용 흔적을 지운다
                    env_file.reset_loaded()
                    applied = env_file.ensure_loaded()
        self.assertEqual(applied, ["AGAIN"])


class LlmModuleIntegrationTest(unittest.TestCase):
    """llm.py를 불러오면 .env가 적용되는지 (Claude SDK처럼 api_key()를 거치지 않는 경로도 덮도록).

    별도 프로세스에서 확인한다. `importlib.reload(llm)`로 같은 프로세스에서 확인하면 llm.py의 클래스
    (LLMError 등)가 새 객체로 바뀌어, 이미 `from llm import LLMError`로 가져다 쓴 다른 테스트 파일
    (test_pipeline.py · test_model_compare.py 등)의 예외 클래스 동일성이 깨진다. 테스트 실행 순서에
    따라 멀쩡한 테스트가 깨지므로 프로세스를 분리해 그 부작용을 피한다.
    """

    def test_llm_module_applies_env_file_on_import(self):
        import subprocess

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("SUBPROCESS_TEST_VAR=applied\n", encoding="utf-8")
            code = (
                "import sys; sys.path.insert(0, {tool_dir!r})\n"
                "from pathlib import Path\n"
                "import env_file\n"
                "env_file.DEFAULT_ENV_PATHS = (Path({path!r}),)\n"
                "import llm  # noqa: F401 (불러오는 시점에 .env가 적용되는지가 이 테스트의 전부다)\n"
                "import os\n"
                "print(os.environ.get('SUBPROCESS_TEST_VAR', ''))\n"
            ).format(tool_dir=str(TOOL_DIR), path=str(path))
            result = subprocess.run([sys.executable, "-I", "-c", code],
                                    capture_output=True, text=True, timeout=10)
        self.assertEqual(result.stdout.strip(), "applied", result.stderr)


if __name__ == "__main__":
    unittest.main()

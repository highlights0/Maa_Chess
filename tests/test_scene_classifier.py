from maa_xiangqi.vision.scene import SceneMode, ScenePhase, classify_scene


def test_user_puzzle_screen_text_is_classified_as_puzzle_board():
    result = classify_scene(["第1关", "小兵", "悔棋", "提示", "分享"])

    assert result.mode is SceneMode.PUZZLE
    assert result.phase is ScenePhase.PUZZLE_BOARD
    assert result.puzzle_level == 1
    assert result.confidence >= 0.95


def test_chinese_numeral_puzzle_level_is_supported():
    result = classify_scene(["第十二关", "残局闯关"])

    assert result.mode is SceneMode.PUZZLE
    assert result.phase is ScenePhase.PUZZLE_BOARD
    assert result.puzzle_level == 12


def test_puzzle_menu_without_level_is_classified_separately():
    result = classify_scene(["残局闯关", "选择关卡"])

    assert result.mode is SceneMode.PUZZLE
    assert result.phase is ScenePhase.PUZZLE_LIST
    assert result.puzzle_level is None


def test_standard_match_labels_are_classified_as_match():
    result = classify_scene(["红方", "黑方", "认输", "求和"])

    assert result.mode is SceneMode.STANDARD
    assert result.phase is ScenePhase.STANDARD_MATCH


def test_unknown_text_does_not_force_a_scene_guess():
    result = classify_scene(["活动公告", "每日签到"])

    assert result.mode is SceneMode.UNKNOWN
    assert result.phase is ScenePhase.UNKNOWN
    assert result.confidence == 0.0

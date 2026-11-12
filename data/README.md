# 棋子分类数据集

建议按类别目录存放经人工核验的棋位小图：

    data/
      dataset/
        empty/
        red_king/
        red_advisor/
        red_elephant/
        red_horse/
        red_rook/
        red_cannon/
        red_pawn/
        black_king/
        black_advisor/
        black_elephant/
        black_horse/
        black_rook/
        black_cannon/
        black_pawn/

采集要求：

1. 标准对局与残局闯关均需采样。
2. 覆盖不同棋盘皮肤、模拟器分辨率、窗口缩放和选中高亮。
3. 同一棋局相邻帧不要全部随机拆分到训练集和验证集，避免数据泄漏。
4. 保留原始截图、校准配置、棋位坐标和人工标签之间的可追溯关系。
5. 未经人工核验的预测标签不能直接进入训练集。

用户截图和运行日志默认保存在被 .gitignore 忽略的 debug/ 下。data/dataset/ 同样被忽略，不会自动上传原始图片。

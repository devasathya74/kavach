from enum import IntEnum

class RankLevel(IntEnum):
    COMMANDANT = 100
    DEPUTY_COMMANDANT = 90
    ASSISTANT_COMMANDANT = 80
    QUARTER_MASTER = 70
    COMPANY_COMMANDER = 60
    SUBEDAR_MAJOR = 50
    PLATOON_COMMANDER = 40
    HEAD_CONSTABLE = 30
    CONSTABLE = 20

RANK_HIERARCHY = {
    "COMMANDANT": RankLevel.COMMANDANT,
    "DEPUTY_COMMANDANT": RankLevel.DEPUTY_COMMANDANT,
    "ASSISTANT_COMMANDANT": RankLevel.ASSISTANT_COMMANDANT,
    "QUARTER_MASTER": RankLevel.QUARTER_MASTER,
    "COMPANY_COMMANDER": RankLevel.COMPANY_COMMANDER,
    "SUBEDAR_MAJOR": RankLevel.SUBEDAR_MAJOR,
    "PLATOON_COMMANDER": RankLevel.PLATOON_COMMANDER,
    "HEAD_CONSTABLE": RankLevel.HEAD_CONSTABLE,
    "CONSTABLE": RankLevel.CONSTABLE,
}

def get_rank_level(rank_code: str) -> int:
    """Returns the numeric authority level for a given rank code."""
    return RANK_HIERARCHY.get(rank_code, 0)

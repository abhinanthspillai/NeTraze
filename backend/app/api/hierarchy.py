import uuid
import logging
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.dependencies.auth import get_current_user
from app.models.domain import User, Project, ProjectMember, Building, Floor, SurveyArea
from app.schemas.hierarchy import (
    ProjectCreate, ProjectUpdate, ProjectOut,
    BuildingCreate, BuildingUpdate, BuildingOut,
    FloorCreate, FloorUpdate, FloorOut,
    SurveyAreaCreate, SurveyAreaUpdate, SurveyAreaOut
)

router = APIRouter(tags=["Hierarchy Management"])
logger = logging.getLogger(__name__)


def _get_project_authorization(project_id: uuid.UUID, current_user: User, db: Session):
    project = db.get(Project, project_id)
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Project not found")

    is_owner = (project.owner_id == current_user.id)
    is_member = is_owner or (
        db.execute(
            select(ProjectMember).where(
                ProjectMember.project_id == project_id,
                ProjectMember.user_id == current_user.id
            )
        ).scalar_one_or_none() is not None
    )

    if not is_member:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Project not found")

    return project, is_owner, is_member


def _get_building_authorization(building_id: uuid.UUID, current_user: User, db: Session):
    building = db.get(Building, building_id)
    if not building:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Building not found")

    project, is_owner, is_member = _get_project_authorization(building.project_id, current_user, db)
    return building, project, is_owner, is_member


def _get_floor_authorization(floor_id: uuid.UUID, current_user: User, db: Session):
    floor = db.get(Floor, floor_id)
    if not floor:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Floor not found")

    building, project, is_owner, is_member = _get_building_authorization(floor.building_id, current_user, db)
    return floor, building, project, is_owner, is_member


def _get_survey_area_authorization(survey_area_id: uuid.UUID, current_user: User, db: Session):
    survey_area = db.get(SurveyArea, survey_area_id)
    if not survey_area:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Survey area not found")

    floor, building, project, is_owner, is_member = _get_floor_authorization(survey_area.floor_id, current_user, db)
    return survey_area, floor, building, project, is_owner, is_member


def _require_mutation_authority(
    current_user: User,
    project: Project,
    is_owner: bool,
    is_member: bool,
    operation: str
):
    if not is_owner:
        logger.warning(
            "Hierarchy mutation denied: operation=%s user_id=%s user_role=%s project_id=%s owner_id=%s is_owner=%s is_member=%s",
            operation,
            current_user.id,
            current_user.role,
            project.id,
            project.owner_id,
            is_owner,
            is_member,
        )
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the project owner may modify hierarchy")


# ==========================================
# PROJECTS
# ==========================================

@router.get("/projects", response_model=List[ProjectOut])
def list_projects(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    stmt = (
        select(Project)
        .outerjoin(ProjectMember, Project.id == ProjectMember.project_id)
        .where(
            (Project.owner_id == current_user.id) | (ProjectMember.user_id == current_user.id)
        )
        .distinct()
        .order_by(Project.name.asc())
    )
    return db.execute(stmt).scalars().all()


@router.post("/projects", response_model=ProjectOut, status_code=status.HTTP_201_CREATED)
def create_project(
    payload: ProjectCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    project_name = payload.name.strip()
    existing_project = db.execute(
        select(Project).where(
            Project.owner_id == current_user.id,
            Project.name == project_name,
            Project.is_active.is_(True)
        )
    ).scalar_one_or_none()
    if existing_project:
        existing_member = db.execute(
            select(ProjectMember).where(
                ProjectMember.project_id == existing_project.id,
                ProjectMember.user_id == current_user.id
            )
        ).scalar_one_or_none()
        if not existing_member:
            db.add(ProjectMember(project_id=existing_project.id, user_id=current_user.id))
            db.commit()
            db.refresh(existing_project)
        return existing_project

    new_project = Project(
        id=uuid.uuid4(),
        owner_id=current_user.id,
        name=project_name,
        is_active=True
    )
    db.add(new_project)

    member_record = ProjectMember(
        project_id=new_project.id,
        user_id=current_user.id
    )
    db.add(member_record)

    db.commit()
    db.refresh(new_project)
    return new_project


@router.get("/projects/{project_id}", response_model=ProjectOut)
def get_project(
    project_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    project, _, _ = _get_project_authorization(project_id, current_user, db)
    return project


@router.patch("/projects/{project_id}", response_model=ProjectOut)
def update_project(
    project_id: uuid.UUID,
    payload: ProjectUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    project, is_owner, is_member = _get_project_authorization(project_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "PATCH /api/v1/projects/{project_id}")

    project.name = payload.name.strip()
    db.commit()
    db.refresh(project)
    return project


# ==========================================
# BUILDINGS
# ==========================================

@router.get("/projects/{project_id}/buildings", response_model=List[BuildingOut])
def list_buildings_for_project(
    project_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    _get_project_authorization(project_id, current_user, db)
    stmt = select(Building).where(Building.project_id == project_id).order_by(Building.name.asc())
    return db.execute(stmt).scalars().all()


@router.post("/projects/{project_id}/buildings", response_model=BuildingOut, status_code=status.HTTP_201_CREATED)
def create_building(
    project_id: uuid.UUID,
    payload: BuildingCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    project, is_owner, is_member = _get_project_authorization(project_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "POST /api/v1/projects/{project_id}/buildings")

    building_name = payload.name.strip()
    existing_building = db.execute(
        select(Building).where(
            Building.project_id == project_id,
            Building.name == building_name
        )
    ).scalar_one_or_none()
    if existing_building:
        return existing_building

    new_building = Building(
        id=uuid.uuid4(),
        project_id=project_id,
        name=building_name
    )
    db.add(new_building)
    db.commit()
    db.refresh(new_building)
    return new_building


@router.get("/buildings/{building_id}", response_model=BuildingOut)
def get_building(
    building_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    building, _, _, _ = _get_building_authorization(building_id, current_user, db)
    return building


@router.patch("/buildings/{building_id}", response_model=BuildingOut)
def update_building(
    building_id: uuid.UUID,
    payload: BuildingUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    building, project, is_owner, is_member = _get_building_authorization(building_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "PATCH /api/v1/buildings/{building_id}")

    building.name = payload.name.strip()
    db.commit()
    db.refresh(building)
    return building


# ==========================================
# FLOORS
# ==========================================

@router.get("/buildings/{building_id}/floors", response_model=List[FloorOut])
def list_floors_for_building(
    building_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    _get_building_authorization(building_id, current_user, db)
    stmt = select(Floor).where(Floor.building_id == building_id).order_by(Floor.name.asc())
    return db.execute(stmt).scalars().all()


@router.post("/buildings/{building_id}/floors", response_model=FloorOut, status_code=status.HTTP_201_CREATED)
def create_floor(
    building_id: uuid.UUID,
    payload: FloorCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    _, project, is_owner, is_member = _get_building_authorization(building_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "POST /api/v1/buildings/{building_id}/floors")

    floor_name = payload.name.strip()
    existing_floor = db.execute(
        select(Floor).where(
            Floor.building_id == building_id,
            Floor.name == floor_name
        )
    ).scalar_one_or_none()
    if existing_floor:
        return existing_floor

    new_floor = Floor(
        id=uuid.uuid4(),
        building_id=building_id,
        name=floor_name
    )
    db.add(new_floor)
    db.commit()
    db.refresh(new_floor)
    return new_floor


@router.get("/floors/{floor_id}", response_model=FloorOut)
def get_floor(
    floor_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    floor, _, _, _, _ = _get_floor_authorization(floor_id, current_user, db)
    return floor


@router.patch("/floors/{floor_id}", response_model=FloorOut)
def update_floor(
    floor_id: uuid.UUID,
    payload: FloorUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    floor, _, project, is_owner, is_member = _get_floor_authorization(floor_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "PATCH /api/v1/floors/{floor_id}")

    floor.name = payload.name.strip()
    db.commit()
    db.refresh(floor)
    return floor


# ==========================================
# SURVEY AREAS
# ==========================================

@router.get("/floors/{floor_id}/survey-areas", response_model=List[SurveyAreaOut])
def list_survey_areas_for_floor(
    floor_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    _get_floor_authorization(floor_id, current_user, db)
    stmt = select(SurveyArea).where(SurveyArea.floor_id == floor_id).order_by(SurveyArea.name.asc())
    return db.execute(stmt).scalars().all()


@router.post("/floors/{floor_id}/survey-areas", response_model=SurveyAreaOut, status_code=status.HTTP_201_CREATED)
def create_survey_area(
    floor_id: uuid.UUID,
    payload: SurveyAreaCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    _, _, project, is_owner, is_member = _get_floor_authorization(floor_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "POST /api/v1/floors/{floor_id}/survey-areas")

    survey_area_name = payload.name.strip()
    existing_survey_area = db.execute(
        select(SurveyArea).where(
            SurveyArea.floor_id == floor_id,
            SurveyArea.name == survey_area_name
        )
    ).scalar_one_or_none()
    if existing_survey_area:
        return existing_survey_area

    new_survey_area = SurveyArea(
        id=uuid.uuid4(),
        floor_id=floor_id,
        name=survey_area_name
    )
    db.add(new_survey_area)
    db.commit()
    db.refresh(new_survey_area)
    return new_survey_area


@router.get("/survey-areas/{survey_area_id}", response_model=SurveyAreaOut)
def get_survey_area(
    survey_area_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    survey_area, _, _, _, _, _ = _get_survey_area_authorization(survey_area_id, current_user, db)
    return survey_area


@router.patch("/survey-areas/{survey_area_id}", response_model=SurveyAreaOut)
def update_survey_area(
    survey_area_id: uuid.UUID,
    payload: SurveyAreaUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    survey_area, _, _, project, is_owner, is_member = _get_survey_area_authorization(survey_area_id, current_user, db)
    _require_mutation_authority(current_user, project, is_owner, is_member, "PATCH /api/v1/survey-areas/{survey_area_id}")

    survey_area.name = payload.name.strip()
    db.commit()
    db.refresh(survey_area)
    return survey_area

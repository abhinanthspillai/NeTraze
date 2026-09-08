import uuid
from datetime import datetime, timezone
from typing import List, Tuple
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.auth import get_current_user
from app.core.database import get_db
from app.models.domain import Building, Floor, FloorPlan, Project, ProjectMember, ScanCycle, SimpleMap, Survey, SurveyArea, User, WifiObservation
from app.schemas.survey import AccessPointOut, ChannelOut, ObservationOut, SurveyCreate, SurveyOut, SurveyUpdate

router = APIRouter(tags=["surveys"])


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _get_survey_area_authorization(
    db: Session, survey_area_id: uuid.UUID, current_user: User
) -> Tuple[SurveyArea, Project, bool, bool]:
    area = db.execute(select(SurveyArea).where(SurveyArea.id == survey_area_id)).scalar_one_or_none()
    if not area:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Survey area not found")

    floor = db.execute(select(Floor).where(Floor.id == area.floor_id)).scalar_one_or_none()
    if not floor:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Floor not found")

    building = db.execute(select(Building).where(Building.id == floor.building_id)).scalar_one_or_none()
    if not building:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Building not found")

    project = db.execute(select(Project).where(Project.id == building.project_id)).scalar_one_or_none()
    if not project:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Project not found")

    is_owner = (project.owner_id == current_user.id) and (current_user.role == "administrator")
    member_record = db.execute(
        select(ProjectMember).where(
            ProjectMember.project_id == project.id, ProjectMember.user_id == current_user.id
        )
    ).scalar_one_or_none()
    is_member = (member_record is not None) or (project.owner_id == current_user.id)

    if not is_member and not is_owner:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Survey area not found")

    return area, project, is_owner, is_member


def _get_survey_authorization(
    db: Session, survey_id: uuid.UUID, current_user: User
) -> Tuple[Survey, Project, bool, bool]:
    survey = db.execute(select(Survey).where(Survey.id == survey_id)).scalar_one_or_none()
    if not survey:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Survey not found")

    _, project, is_owner, is_member = _get_survey_area_authorization(db, survey.survey_area_id, current_user)
    return survey, project, is_owner, is_member


def _ensure_mode_artifact(
    db: Session,
    survey_area_id: uuid.UUID,
    payload: SurveyCreate,
    current_user: User
) -> Tuple[uuid.UUID | None, uuid.UUID | None]:
    if payload.mode == "floor_plan":
        floor_plan_id = payload.floor_plan_id or uuid.uuid4()
        floor_plan = db.execute(select(FloorPlan).where(FloorPlan.id == floor_plan_id)).scalar_one_or_none()
        if not floor_plan:
            db.add(FloorPlan(
                id=floor_plan_id,
                survey_area_id=survey_area_id,
                storage_path="normalized://phase1-placeholder",
                original_filename="Normalized Phase 1 workspace",
                width_px=None,
                height_px=None,
                uploaded_by=current_user.id
            ))
        return floor_plan_id, None

    if payload.mode == "simple_map":
        simple_map_id = payload.simple_map_id or uuid.uuid4()
        simple_map = db.execute(select(SimpleMap).where(SimpleMap.id == simple_map_id)).scalar_one_or_none()
        if not simple_map:
            db.add(SimpleMap(
                id=simple_map_id,
                survey_area_id=survey_area_id,
                artifact_reference="normalized://phase1-simple-map",
                created_by=current_user.id
            ))
        return None, simple_map_id

    return None, None


@router.post("/survey-areas/{survey_area_id}/surveys", response_model=SurveyOut, status_code=status.HTTP_201_CREATED)
def create_survey(
    survey_area_id: uuid.UUID,
    payload: SurveyCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _, project, _, is_member = _get_survey_area_authorization(db, survey_area_id, current_user)

    if not is_member:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to create survey in this project")

    survey_id = payload.id or uuid.uuid4()

    existing_survey = db.execute(select(Survey).where(Survey.id == survey_id)).scalar_one_or_none()
    if existing_survey:
        return existing_survey

    started_at = payload.started_at or utc_now()
    floor_plan_id, simple_map_id = _ensure_mode_artifact(db, survey_area_id, payload, current_user)

    survey = Survey(
        id=survey_id,
        survey_area_id=survey_area_id,
        title=payload.title,
        mode=payload.mode,
        status="in_progress",
        floor_plan_id=floor_plan_id,
        simple_map_id=simple_map_id,
        created_by=current_user.id,
        started_at=started_at,
        created_at=utc_now(),
        updated_at=utc_now(),
    )
    db.add(survey)
    db.commit()
    db.refresh(survey)
    return survey


@router.get("/survey-areas/{survey_area_id}/surveys", response_model=List[SurveyOut])
def get_surveys_for_area(
    survey_area_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _get_survey_area_authorization(db, survey_area_id, current_user)
    surveys = db.execute(
        select(Survey).where(Survey.survey_area_id == survey_area_id).order_by(Survey.created_at.desc())
    ).scalars().all()
    return list(surveys)


@router.get("/surveys/{survey_id}", response_model=SurveyOut)
def get_survey(
    survey_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    survey, _, _, _ = _get_survey_authorization(db, survey_id, current_user)
    return survey


@router.patch("/surveys/{survey_id}", response_model=SurveyOut)
def update_survey(
    survey_id: uuid.UUID,
    payload: SurveyUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    survey, _, is_owner, is_member = _get_survey_authorization(db, survey_id, current_user)

    if not is_member and not is_owner:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to modify this survey")

    if survey.status == "completed":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Survey is completed and frozen. Further modifications are prohibited."
        )

    if payload.title is not None:
        survey.title = payload.title
        survey.updated_at = utc_now()
        db.commit()
        db.refresh(survey)

    return survey


# STAGE K: Approved D080 Completion Barrier Endpoint
@router.post("/surveys/{survey_id}/complete", response_model=SurveyOut, status_code=status.HTTP_200_OK)
def complete_survey(
    survey_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    survey, _, is_owner, is_member = _get_survey_authorization(db, survey_id, current_user)

    if not is_member and not is_owner:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to complete this survey")

    if survey.status == "completed":
        return survey

    now = utc_now()
    survey.status = "completed"
    survey.completed_at = now
    survey.updated_at = now
    db.commit()
    db.refresh(survey)

    return survey


# STAGE J: Approved D034 Raw Evidence Query Routes
@router.get("/surveys/{survey_id}/access-points", response_model=List[AccessPointOut])
def get_survey_access_points(
    survey_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _get_survey_authorization(db, survey_id, current_user)

    stmt = (
        select(
            WifiObservation.bssid,
            func.max(WifiObservation.ssid).label("ssid"),
            func.count(WifiObservation.id).label("observation_count"),
            func.max(WifiObservation.rssi_dbm).label("max_rssi"),
            func.avg(WifiObservation.rssi_dbm).label("avg_rssi"),
            func.max(WifiObservation.frequency_mhz).label("latest_frequency"),
        )
        .join(ScanCycle, WifiObservation.scan_cycle_id == ScanCycle.id)
        .where(ScanCycle.survey_id == survey_id)
        .group_by(WifiObservation.bssid)
        .order_by(func.count(WifiObservation.id).desc())
    )

    rows = db.execute(stmt).all()
    return [
        AccessPointOut(
            bssid=row.bssid,
            ssid=row.ssid,
            observation_count=row.observation_count,
            max_rssi=row.max_rssi,
            avg_rssi=float(row.avg_rssi),
            latest_frequency=row.latest_frequency,
        )
        for row in rows
    ]


@router.get("/surveys/{survey_id}/channels", response_model=List[ChannelOut])
def get_survey_channels(
    survey_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _get_survey_authorization(db, survey_id, current_user)

    stmt = (
        select(
            WifiObservation.channel,
            WifiObservation.frequency_mhz,
            func.count(WifiObservation.id).label("observation_count"),
            func.count(func.distinct(WifiObservation.bssid)).label("ap_count"),
        )
        .join(ScanCycle, WifiObservation.scan_cycle_id == ScanCycle.id)
        .where(ScanCycle.survey_id == survey_id)
        .group_by(WifiObservation.channel, WifiObservation.frequency_mhz)
        .order_by(WifiObservation.frequency_mhz.asc())
    )

    rows = db.execute(stmt).all()
    return [
        ChannelOut(
            channel=row.channel,
            frequency_mhz=row.frequency_mhz,
            observation_count=row.observation_count,
            ap_count=row.ap_count,
        )
        for row in rows
    ]


@router.get("/surveys/{survey_id}/observations", response_model=List[ObservationOut])
def get_survey_observations(
    survey_id: uuid.UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _get_survey_authorization(db, survey_id, current_user)

    stmt = (
        select(
            WifiObservation.id,
            WifiObservation.scan_cycle_id,
            WifiObservation.bssid,
            WifiObservation.ssid,
            WifiObservation.rssi_dbm,
            WifiObservation.frequency_mhz,
            WifiObservation.channel,
            WifiObservation.channel_source,
            WifiObservation.capabilities,
            ScanCycle.captured_at_wallclock,
        )
        .join(ScanCycle, WifiObservation.scan_cycle_id == ScanCycle.id)
        .where(ScanCycle.survey_id == survey_id)
        .order_by(ScanCycle.captured_at_wallclock.desc())
    )

    rows = db.execute(stmt).all()
    return [
        ObservationOut(
            id=row.id,
            scan_cycle_id=row.scan_cycle_id,
            bssid=row.bssid,
            ssid=row.ssid,
            rssi_dbm=row.rssi_dbm,
            frequency_mhz=row.frequency_mhz,
            channel=row.channel,
            channel_source=row.channel_source,
            capabilities=row.capabilities,
            captured_at_wallclock=row.captured_at_wallclock,
        )
        for row in rows
    ]

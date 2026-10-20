#include "drape_frontend/engine_context.hpp"
#include "drape_frontend/tile_info.hpp"
#include "drape_frontend/map_data_provider.hpp"
#include "drape_frontend/metaline_manager.hpp"
#include "drape_frontend/message_queue.hpp"
#include <cassert>
#include <iostream>
#include <stdexcept>
namespace { df::MessageQueue queue; }
namespace df {
void ThreadsCommutator::PostMessage(ThreadName thread,drape_ptr<Message> && message,MessagePriority priority)
{assert(thread==ResourceUploadThread);queue.PushMessage(std::move(message),priority);}
}
struct Shape : df::MapShape {
 static inline int destroyed=0;
 ~Shape()override{++destroyed;}
 void Draw(ref_ptr<dp::GraphicsContext>,ref_ptr<dp::Batcher>,ref_ptr<dp::TextureManager>)const override{}
};
df::TMapShapes shapes(size_t count){df::TMapShapes result;while(count--)result.push_back(make_unique_dp<Shape>());return result;}
int main(){
 using namespace df;ThreadsCommutator bus;MetalineManager metalines;
 auto make=[&]{return make_unique_dp<EngineContext>(TileKey{},make_ref(&bus),nullptr,make_ref(&metalines),CustomFeaturesContextWeakPtr{},false,false,false,0,dp::BackgroundMode::Default,1.0f);};
 auto context=make();context->BeginReadTile();context->Flush(shapes(130));context->FlushOverlays(shapes(1));context->EndReadTile();
 int starts=0,ends=0,geometry=0,overlays=0;std::vector<size_t> batches;
 while(auto m=queue.PopMessage(false)){
  switch(m->GetType()){
   case Message::Type::TileReadStarted:++starts;break;
   case Message::Type::TileReadEnded:++ends;break;
   case Message::Type::MapShapeReaded:{auto&s=static_cast<MapShapeReadedMessage*>(m.get())->GetShapes();batches.push_back(s.size());geometry+=s.size();break;}
   case Message::Type::OverlayMapShapeReaded:++overlays;break;
   default:assert(false);
  }
 }
 assert(starts==1&&ends==1&&geometry==130&&overlays==1);assert((batches==std::vector<size_t>{64,64,2}));assert(Shape::destroyed==131);
 context=make();context->BeginReadTile();context->Flush(shapes(65));context->Cancel();
 queue.InstantFilter(MapShapeReadedMessage::IsCancelledMessage);context->FlushOverlays(shapes(1));context->EndReadTile();
 assert(queue.PopMessage(false)->GetType()==Message::Type::TileReadStarted);assert(queue.PopMessage(false)->GetType()==Message::Type::TileReadEnded);assert(!queue.PopMessage(false));assert(Shape::destroyed==197);
 // A producer racing after the cancellation sweep still carries the old read's token.
 context=make();context->BeginReadTile();context->Flush(shapes(64));context->EndReadTile();context->Cancel();
 int rejected=0;starts=ends=0;while(auto m=queue.PopMessage(false)){if(MapShapeReadedMessage::IsCancelledMessage(make_ref(m)))++rejected;else if(m->GetType()==Message::Type::TileReadStarted)++starts;else if(m->GetType()==Message::Type::TileReadEnded)++ends;}
 assert(rejected==1&&starts==1&&ends==1);
 // TileInfo pairs starts/ends on normal reads, cancellation and provider exceptions.
 for(int mode=0;mode<3;++mode){TileInfo tile(make());MapDataProvider provider;provider.fail=mode==2;if(mode==1)tile.Cancel();bool failed=false;
 try{tile.ReadFeatures(provider);}catch(std::runtime_error const&){failed=true;}assert(failed==(mode==2));
 assert(queue.PopMessage(false)->GetType()==Message::Type::TileReadStarted);assert(queue.PopMessage(false)->GetType()==Message::Type::TileReadEnded);assert(!queue.PopMessage(false));}
 // A new read of the same tile is independent of the old read's cancellation token.
 auto old=make(),fresh=make();old->Cancel();fresh->Flush(shapes(1));fresh->EndReadTile();auto payload=queue.PopMessage(false);assert(!MapShapeReadedMessage::IsCancelledMessage(make_ref(payload)));
 std::cout<<"PASS complete EngineContext/TileInfo/MessageQueue owners: batch/tails/cancel races/read pairing\n";
}
